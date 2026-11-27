// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.projectView

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ClassesTreeStructureProvider
import com.intellij.ide.projectView.impl.PackageViewPane
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.projectView.impl.nodes.PackageElementNode
import com.intellij.ide.projectView.impl.nodes.PackageViewProjectNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Queryable
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectViewTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.IconManager
import com.intellij.ui.icons.CoreIconManager
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path

/**
 * A heavy Java project with a copy of a test data directory, as [com.intellij.testFramework.TestSourceBasedTestCase] gives it.
 *
 * Before the test, the fixture copies the directory [testDirectoryName] of `<testDataPath>/<testPath>` to a temporary directory.
 * The copy becomes a content root of the main module, and its `src` directory becomes a source root.
 * When [testPath] is `null`, the fixture copies nothing.
 * Then the fixture sets up the project view of the project with [ProjectViewTestUtil.setupImpl].
 *
 * The project, its `.ipr` file and the main module get the name of the test method, as on JUnit 3.
 * See [HeavyJavaCodeInsightFixture] for the rest of the project and for the threading of the members.
 *
 * Create it with [testSourceBasedFixture].
 */
@TestOnly
open class TestSourceBasedFixture(
  context: Context,
  testDataPath: String?,
  private val testPath: String?,
  testDirectoryName: String?,
  private val iconRequired: Boolean,
) : HeavyJavaCodeInsightFixture(context, testDataPath ?: defaultTestDataPath(context)) {
  private lateinit var tempDirectory: Path

  /**
   * The name of the test data directory of the test. By default, it is the test name without the `test` prefix,
   * with a lowercase first letter.
   */
  val testDirectoryName: String = testDirectoryName ?: getTestName(true)

  /**
   * The copy of the test data directory. It is the content root of the main module.
   */
  val contentRoot: VirtualFile?
    get() = VirtualFileManager.getInstance().findFileByNioPath(tempDirectory.resolve(testDirectoryName))

  /**
   * The PSI directory of [contentRoot]. It is `getContentDirectory()` of [com.intellij.testFramework.TestSourceBasedTestCase].
   */
  val contentRootDirectory: PsiDirectory?
    get() = ReadAction.computeBlocking<PsiDirectory?, RuntimeException> { psiManager.findDirectory(contentRoot!!) }

  /**
   * The PSI directory of the `src` directory of [contentRoot].
   */
  val srcDirectory: PsiDirectory?
    get() = getPackageDirectoryByPath("src")

  /**
   * Returns the PSI directory of [packageRelativePath] in the `src` directory of [contentRoot].
   */
  fun getPackageDirectory(packageRelativePath: String): PsiDirectory? = getPackageDirectoryByPath("src/$packageRelativePath")

  private fun getPackageDirectoryByPath(relativePath: String): PsiDirectory? =
    ReadAction.computeBlocking<PsiDirectory?, RuntimeException> {
      psiManager.findDirectory(checkNotNull(contentRoot!!.findFileByRelativePath(relativePath)) { relativePath })
    }

  override fun setUp() {
    if (iconRequired) {
      // ensure that IconLoader will not use a fake empty icon
      IconManager.activate(CoreIconManager())
    }
    super.setUp()
    val tempDir = createTempVirtualDir()
    tempDirectory = tempDir.toNioPath()
    if (testPath != null) {
      val testRoot = Path.of(testDataPath, testPath)
      check(Files.isDirectory(testRoot)) { testRoot.toString() }
      val currentTestRoot = testRoot.resolve(testDirectoryName)
      check(Files.isDirectory(currentTestRoot)) { currentTestRoot.toString() }
      NioFiles.copyRecursively(currentTestRoot, tempDirectory.resolve(testDirectoryName))
      tempDir.refresh(false, true)
      WriteAction.runAndWait<RuntimeException> {
        val contentRoot = checkNotNull(tempDir.findChild(testDirectoryName)) { testDirectoryName }
        PsiTestUtil.addContentRoot(module, contentRoot)
        val src = contentRoot.findChild("src")
        if (src != null) {
          PsiTestUtil.addSourceRoot(module, src)
        }
      }
    }
    runInEdtAndWait {
      ProjectViewTestUtil.setupImpl(project, true)
    }
  }

  override fun tearDown() {
    try {
      super.tearDown()
    }
    finally {
      if (iconRequired) {
        IconManager.deactivate()
        IconLoader.clearCacheInTests()
      }
    }
  }

  companion object {
    /**
     * The test data path of [com.intellij.testFramework.TestSourceBasedTestCase]: the Java test data under Bazel,
     * and the test data of the test class otherwise.
     */
    private fun defaultTestDataPath(context: Context): String =
      if (BazelTestUtil.isUnderBazelTest) JavaTestUtil.getJavaTestDataPath() else PathManagerEx.getTestDataPath(context.testClass)

    /**
     * Creates a [TestSourceBasedFixture] with a new project for each test. Declare it as an instance field.
     * The project has an `.ipr` file, as the project of [com.intellij.testFramework.TestSourceBasedTestCase] has.
     *
     * [testPath] is `getTestPath()`, and [testDirectoryName] is `getTestDirectoryName()` of the JUnit 3 test.
     * [testDataPath] replaces the default test data path. When [iconRequired] is `true`, the fixture activates
     * the real icon manager for the test, as `isIconRequired()` of a JUnit 3 test did.
     */
    @TestOnly
    @JvmStatic
    @JvmOverloads
    fun testSourceBasedFixture(
      testPath: String?,
      testDirectoryName: String? = null,
      testDataPath: String? = null,
      iconRequired: Boolean = false,
    ): TestFixture<TestSourceBasedFixture> =
      heavyJavaCodeInsightTestNamedSubFixture("testSourceBasedFixture", directoryBasedProject = false) { context ->
        TestSourceBasedFixture(context, testDataPath, testPath, testDirectoryName, iconRequired)
      }
  }
}

/**
 * The project of a project view test, as [BaseProjectViewTestCase] gives it: a [TestSourceBasedFixture] on a
 * directory-based project with the test data of `projectView`, and a [structure] of the project view.
 *
 * The helpers print the [structure] in a read action. The project view panes and their trees need the EDT.
 *
 * Create it with [projectViewFixture].
 */
@TestOnly
open class ProjectViewFixture(
  context: Context,
  testPath: String?,
  iconRequired: Boolean,
  private val packageViewPaneId: String,
  /**
   * The print info of the [structure] helpers. It is `myPrintInfo` of [BaseProjectViewTestCase].
   */
  val printInfo: Queryable.PrintInfo?,
) : TestSourceBasedFixture(context, null, testPath, null, iconRequired) {
  /**
   * The structure of the project view. The test root disposable disposes it.
   * Its root is the project node of the pane [packageViewPaneId].
   */
  lateinit var structure: TestProjectTreeStructure
    private set

  /**
   * The PSI directory of the package `com/package1` in the `src` directory of the content root.
   */
  val packageDirectory: PsiDirectory?
    get() = getPackageDirectory(PACKAGE_RELATIVE_PATH)

  override fun setUp() {
    super.setUp()
    structure = object : TestProjectTreeStructure(project, testRootDisposable) {
      override fun createRoot(project: Project, settings: ViewSettings): AbstractTreeNode<*> =
        when (packageViewPaneId) {
          ProjectViewPane.ID -> super.createRoot(project, settings)
          PackageViewPane.ID -> PackageViewProjectNode(project, settings)
          else -> throw IllegalStateException("Unexpected value: $packageViewPaneId")
        }
    }
  }

  /**
   * Sets the [ClassesTreeStructureProvider] as the only provider of the [structure].
   */
  fun useStandardProviders() {
    structure.setProviders(ClassesTreeStructureProvider(project))
  }

  /**
   * Checks the structure under [packageDirectory] with at most 17 rows.
   */
  fun assertStructureEqual(packageDirectory: PsiDirectory?, expected: String) {
    assertStructureEqual(packageDirectory, expected, 17)
  }

  /**
   * Checks the structure under [packageDirectory] with at most [maxRowCount] rows.
   */
  fun assertStructureEqual(packageDirectory: PsiDirectory?, expected: String, maxRowCount: Int) {
    checkNotNull(packageDirectory)
    assertStructureEqual(expected, maxRowCount, PsiDirectoryNode(project, packageDirectory, structure))
  }

  /**
   * Checks the structure under the root element of the [structure].
   */
  fun assertStructureEqual(expected: String) {
    assertStructureEqual(expected, -1, structure.rootElement)
  }

  private fun assertStructureEqual(expected: String, maxRowCount: Int, rootNode: Any) {
    ReadAction.runBlocking<RuntimeException> {
      ProjectViewTestUtil.assertStructureEqual(structure, expected, maxRowCount, PlatformTestUtil.createComparator(printInfo), rootNode,
                                               printInfo)
    }
  }

  companion object {
    /**
     * The package of [packageDirectory]. It is `getPackageRelativePath()` of [BaseProjectViewTestCase].
     */
    const val PACKAGE_RELATIVE_PATH: String = "com/package1"

    /**
     * Checks that each node under [rootElement] of [structure] contains the files of its children.
     * It is `checkContainsMethod` of [BaseProjectViewTestCase].
     */
    @JvmStatic
    fun checkContainsMethod(rootElement: Any, structure: AbstractTreeStructure) {
      ProjectViewTestUtil.checkContainsMethod(rootElement, structure) { kid ->
        if (kid is PackageElementNode) kid.virtualFiles else null
      }
    }

    /**
     * Sorts [classes] by name. It is `sortClassesByName` of [BaseProjectViewTestCase].
     */
    @JvmStatic
    fun sortClassesByName(classes: Array<PsiClass>) {
      classes.sortWith(compareBy { it.name })
    }

    /**
     * Creates a [ProjectViewFixture] with a new directory-based project for each test. Declare it as an instance field.
     *
     * [printInfo] is `myPrintInfo` of [BaseProjectViewTestCase]. [iconRequired] is `isIconRequired()` of the JUnit 3 test.
     * [packageViewPaneId] chooses the root of the [ProjectViewFixture.structure]: the project pane or the packages pane.
     * [testPath] is `getTestPath()` of the JUnit 3 test.
     */
    @TestOnly
    @JvmStatic
    @JvmOverloads
    fun projectViewFixture(
      printInfo: Queryable.PrintInfo? = null,
      iconRequired: Boolean = false,
      packageViewPaneId: String = ProjectViewPane.ID,
      testPath: String? = "projectView",
    ): TestFixture<ProjectViewFixture> =
      heavyJavaCodeInsightTestNamedSubFixture("projectViewFixture", directoryBasedProject = true) { context ->
        ProjectViewFixture(context, testPath, iconRequired, packageViewPaneId, printInfo)
      }
  }
}
