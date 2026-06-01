package com.intellij.platform.lsp.common

import com.intellij.codeInsight.daemon.impl.MockWolfTheProblemSolver
import com.intellij.codeInsight.daemon.impl.WolfTheProblemSolverImpl
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture

/**
 * Registers the [PROBLEM_FILE_HIGHLIGHT_FILTER_EP] EP and a filter extension on the project's extension area.
 *
 * The EP is declared in `LangExtensionPoints.xml` with `area="IDEA_PROJECT"`, which isn't loaded in the test environment.
 * Without it, [WolfTheProblemSolverImpl.isToBeHighlighted] returns `false` and
 * [WolfTheProblemSolverImpl.reportProblemsFromExternalSource] silently ignores the call.
 *
 * @param createFilter factory for the filter extension; defaults to accept-all.
 */
internal fun TestFixture<Project>.problemFileHighlightFilterFixture(
  createFilter: (Project) -> Condition<VirtualFile> = { Condition { true } },
): TestFixture<Condition<VirtualFile>> = testFixture("problemFileHighlightFilter") {
  val project = this@problemFileHighlightFilterFixture.init()
  val disposable = Disposer.newDisposable("problemFileHighlightFilter")

  val area = project.extensionArea
  if (!area.hasExtensionPoint(PROBLEM_FILE_HIGHLIGHT_FILTER_EP)) {
    area.registerExtensionPoint(PROBLEM_FILE_HIGHLIGHT_FILTER_EP, Condition::class.java.name, ExtensionPoint.Kind.INTERFACE, true)
    Disposer.register(disposable) { area.unregisterExtensionPoint(PROBLEM_FILE_HIGHLIGHT_FILTER_EP) }
  }
  val ep = area.getExtensionPoint<Condition<VirtualFile>>(PROBLEM_FILE_HIGHLIGHT_FILTER_EP)
  val filter = createFilter(project)
  ep.registerExtension(filter, disposable)

  initialized(filter) { Disposer.dispose(disposable) }
}

/**
 * @see com.intellij.problems.WolfTheProblemSolver.FILTER_EP_NAME
 */
private const val PROBLEM_FILE_HIGHLIGHT_FILTER_EP = "com.intellij.problemFileHighlightFilter"

/**
 * Wires [MockWolfTheProblemSolver] with a real [WolfTheProblemSolverImpl] delegate.
 *
 * In headless mode (tests), [WolfTheProblemSolver.getInstance] returns [MockWolfTheProblemSolver]
 * (see `headlessImplementation` in `PlatformLangComponents.xml`), which makes all operations no-ops.
 * We wire in a real solver via [MockWolfTheProblemSolver.setDelegate],
 * following the same pattern as `WolfTheProblemSolverTest.prepareWolf`.
 *
 * Use the returned [MockWolfTheProblemSolver] directly instead of [WolfTheProblemSolver.getInstance].
 *
 * @param filterFixture ensures the `problemFileHighlightFilter` EP is registered before the wolf delegate is wired.
 */
internal fun wolfFixture(
  projectFixture: TestFixture<Project>,
  filterFixture: TestFixture<Condition<VirtualFile>>,
): TestFixture<MockWolfTheProblemSolver> = testFixture("wolfFixture") {
  val project = projectFixture.init()
  filterFixture.init()
  val disposable = Disposer.newDisposable("wolfFixture")

  val mock = WolfTheProblemSolver.getInstance(project) as MockWolfTheProblemSolver
  val realSolver = WolfTheProblemSolverImpl.createTestInstance(project) as WolfTheProblemSolverImpl
  mock.setDelegate(realSolver)
  Disposer.register(disposable, realSolver)
  Disposer.register(disposable) { mock.resetDelegate() }

  initialized(mock) { Disposer.dispose(disposable) }
}