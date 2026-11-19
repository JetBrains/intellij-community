// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.JavaTestUtil
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.InspectionEP
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase
import com.intellij.codeInspection.deadCode.UnusedDeclarationPresentation
import com.intellij.codeInspection.ex.EntryPointsManagerBase
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.codeInspection.reference.EntryPoint
import com.intellij.codeInspection.reference.RefElement
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertNotNull
import java.nio.file.Path
import java.util.function.BiFunction
import java.util.function.Function

/**
 * The JUnit 5 form of [JavaInspectionTestCase]. Create it with [javaInspectionFixture].
 *
 * The members are the helpers of [JavaInspectionTestCase] with the same names and the same checks.
 * Call them from any thread. [doTest] and [runTool] run on the EDT, as JUnit 3 ran the global inspections.
 */
@TestOnly
class JavaInspectionFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  private val analysisScope: BiFunction<Project, VirtualFile, AnalysisScope>?,
) {
  // The `ext_src` directory of the last runTool call. Its classes are entry points of the dead code analysis.
  internal var extSrc: VirtualFile? = null

  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  fun doTest(@NonNls folderName: String, tool: LocalInspectionTool) {
    doTest(folderName, LocalInspectionToolWrapper(tool))
  }

  fun doTest(@NonNls folderName: String, tool: GlobalInspectionTool) {
    doTest(folderName, GlobalInspectionToolWrapper(tool))
  }

  fun doTest(@NonNls folderName: String, tool: GlobalInspectionTool, checkRange: Boolean) {
    doTest(folderName, GlobalInspectionToolWrapper(tool), checkRange)
  }

  fun doTest(@NonNls folderName: String, tool: GlobalInspectionTool, checkRange: Boolean, runDeadCodeFirst: Boolean) {
    doTest(folderName, GlobalInspectionToolWrapper(tool), checkRange, runDeadCodeFirst)
  }

  fun doTest(@NonNls folderName: String, tool: InspectionToolWrapper<*, *>) {
    doTest(folderName, tool, false)
  }

  fun doTest(@NonNls folderName: String, tool: InspectionToolWrapper<*, *>, checkRange: Boolean) {
    doTest(folderName, tool, checkRange, false)
  }

  /**
   * Copies the test data directory [folderName] to the project, runs [toolWrapper] and [additional] on it,
   * and compares the results with the `expected.xml` files of the directory.
   * With [runDeadCodeFirst], the unused declaration inspection runs first. It runs on the EDT.
   */
  fun doTest(
    @NonNls folderName: String,
    toolWrapper: InspectionToolWrapper<*, *>,
    checkRange: Boolean,
    runDeadCodeFirst: Boolean,
    vararg additional: InspectionToolWrapper<*, *>,
  ) {
    runInEdtAndWait {
      val testDir = fixture.testDataPath + "/" + folderName
      val tools = buildList {
        if (runDeadCodeFirst) {
          add(getUnusedDeclarationWrapper())
        }
        add(toolWrapper)
        addAll(additional)
      }
      val context = runTool(folderName, toolWrapper, tools)
      try {
        InspectionTestUtil.compareToolResults(context, checkRange, testDir, listOf(toolWrapper) + additional)
      }
      finally {
        context.cleanup()
      }
    }
  }

  /**
   * Copies the test data directory [testName] to the project and runs [toolWrapper] on it with [tools] in the global context.
   * The `src` subdirectory goes to the source root, `ext_src` and `test_src` go to the source folders of [JavaInspectionProjectDescriptor].
   * The caller cleans the returned context up. It runs on the EDT.
   */
  fun runTool(
    testName: String,
    toolWrapper: InspectionToolWrapper<*, *>,
    tools: List<InspectionToolWrapper<*, *>>,
  ): GlobalInspectionContextImpl = runInEdtAndGet {
    val file = Path.of(fixture.testDataPath, testName).toAbsolutePath()
    val projectDir = StandardFileSystems.local().refreshAndFindFileByPath(file.toString())
    assertNotNull(projectDir) { "projectDir not found: $file" }

    val srcDir = if (projectDir!!.findChild("src") != null) {
      fixture.copyDirectoryToProject("$testName/src", "")
    }
    else {
      fixture.copyDirectoryToProject(testName, "")
    }
    if (projectDir.findChild("ext_src") != null) {
      extSrc = fixture.copyDirectoryToProject("$testName/ext_src", "ext_src")
    }
    if (projectDir.findChild("test_src") != null) {
      fixture.copyDirectoryToProject("$testName/test_src", "test_src")
    }

    val scope = createAnalysisScope(srcDir)
    val globalContext = createGlobalContextForTool(scope, fixture.project, tools)
    InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
    globalContext
  }

  /**
   * Returns the analysis scope of [sourceDir]: the scope of the factory, or else the directory of [sourceDir].
   */
  fun createAnalysisScope(sourceDir: VirtualFile): AnalysisScope =
    analysisScope?.apply(fixture.project, sourceDir) ?: directoryScope(fixture.project, sourceDir)

  companion object {
    /**
     * Returns the wrapper of the unused declaration inspection, as [JavaInspectionTestCase.getUnusedDeclarationWrapper] does.
     */
    @JvmStatic
    fun getUnusedDeclarationWrapper(): GlobalInspectionToolWrapper {
      val ep = InspectionEP(UnusedDeclarationInspection::class.java.name,
                            DefaultPluginDescriptor(PluginId.getId("JavaInspectionTestCase.getUnusedDeclarationWrapper"),
                                                    JavaInspectionFixture::class.java.classLoader))
      ep.presentation = UnusedDeclarationPresentation::class.java.name
      ep.shortName = UnusedDeclarationInspectionBase.SHORT_NAME
      ep.displayName = UnusedDeclarationInspectionBase.getDisplayNameText()
      return GlobalInspectionToolWrapper(ep)
    }

    /**
     * Returns the analysis scope of the directory [sourceDir], as [JavaInspectionTestCase.createAnalysisScope] does.
     */
    @JvmStatic
    fun directoryScope(project: Project, sourceDir: VirtualFile): AnalysisScope =
      AnalysisScope(PsiManager.getInstance(project).findDirectory(sourceDir)!!)
  }
}

/**
 * The project descriptor of [JavaInspectionTestCase]. The content root also has the source folder `ext_src`
 * and the test source folder `test_src`.
 *
 * It has no `equals`, so keep one instance for each class, for example in a constant.
 */
@TestOnly
open class JavaInspectionProjectDescriptor : DefaultLightProjectDescriptor() {
  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    contentEntry.addSourceFolder(contentEntry.url + "/ext_src", false)
    contentEntry.addSourceFolder(contentEntry.url + "/test_src", true)
  }

  companion object {
    /**
     * The descriptor of [JavaInspectionTestCase] and of [javaInspectionProjectFixture].
     */
    @JvmField
    val INSTANCE: LightProjectDescriptor = JavaInspectionProjectDescriptor()
  }
}

/**
 * Creates the project and the module that the tests of one class share, with [JavaInspectionProjectDescriptor].
 * Declare it in a static field or in a companion object.
 *
 * JUnit 3 kept one light project for all the tests with this descriptor.
 * So a test that changes a project setting must set it back, as before.
 */
@TestOnly
fun javaInspectionProjectFixture(): TestFixture<LightJavaProject> = lightJavaProjectFixture(JavaInspectionProjectDescriptor.INSTANCE)

/**
 * Creates a [JavaInspectionFixture] for one test, as [JavaInspectionTestCase.setUp] does. Declare it as an instance field.
 *
 * [testDataPath] is the absolute test data path, as an override of [JavaInspectionTestCase.getTestDataPath] gives it.
 * By default, it is the `inspection` directory of the Java test data.
 * [analysisScope] gives the scope of the inspections for the project and the source directory of the test,
 * as an override of [JavaInspectionTestCase.createAnalysisScope] does. By default, the scope is the source directory.
 * [descriptor] gets the name of the test without the `test` prefix and returns the descriptor of the test,
 * see [lightJavaCodeInsightFixture].
 */
@TestOnly
@JvmOverloads
fun javaInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
  analysisScope: BiFunction<Project, VirtualFile, AnalysisScope>? = null,
  descriptor: Function<String, LightProjectDescriptor>? = null,
): TestFixture<JavaInspectionFixture> =
  javaInspectionFixtureOn(testFixture("javaInspectionCodeInsightFixture") {
    val fixture = lightJavaCodeInsightFixture(projectFixture, descriptor).init()
    fixture.testDataPath = testDataPath ?: (JavaTestUtil.getJavaTestDataPath() + "/inspection/")
    initialized(fixture) {}
  }, null, analysisScope)

/**
 * Creates a [JavaInspectionFixture] with a new project for each test, as [javaInspectionFixture] does with the project of a class.
 * Declare it as an instance field.
 *
 * [descriptor] gets the name of the test method without the `test` prefix and returns the descriptor of the project.
 * JUnit 3 created a new light project for each test with a descriptor that had no `equals`.
 * Use this factory only for a class whose tests leave a project state that the fixture cannot reset, for example a project library
 * or a virtual file pointer of the module.
 */
@TestOnly
@JvmOverloads
fun javaInspectionFixtureWithProjectPerTest(
  descriptor: Function<String, LightProjectDescriptor>,
  testDataPath: String? = null,
  analysisScope: BiFunction<Project, VirtualFile, AnalysisScope>? = null,
): TestFixture<JavaInspectionFixture> = testFixture("javaInspectionFixtureWithProjectPerTest") { context ->
  val testName = PlatformTestUtil.getTestName(context.extensionContext.requiredTestMethod.name, false)
  val projectFixture = lightJavaProjectFixture(descriptor.apply(testName))
  initialized(javaInspectionFixture(projectFixture, testDataPath, analysisScope).init()) {}
}

/**
 * Creates a [JavaInspectionFixture] on the code insight fixture of [codeInsightFixture], as the other factory does.
 * Use it for a project with its own fixture, for example a JPA project.
 * [testDataPath] is the absolute test data path. By default, the fixture keeps the test data path of [codeInsightFixture].
 */
@TestOnly
@JvmOverloads
fun javaInspectionFixtureOn(
  codeInsightFixture: TestFixture<LightJavaCodeInsightTestFixture>,
  testDataPath: String? = null,
  analysisScope: BiFunction<Project, VirtualFile, AnalysisScope>? = null,
): TestFixture<JavaInspectionFixture> = testFixture("javaInspectionFixture") {
  val fixture = codeInsightFixture.init()
  if (testDataPath != null) {
    fixture.testDataPath = testDataPath
  }
  val inspectionFixture = JavaInspectionFixture(fixture, analysisScope)
  val entryPoint = object : EntryPoint() {
    override fun getDisplayName(): String = "duh"

    override fun isEntryPoint(refElement: RefElement, psiElement: PsiElement): Boolean = isEntryPoint(psiElement)

    override fun isEntryPoint(psiElement: PsiElement): Boolean {
      val extSrc = inspectionFixture.extSrc
      return extSrc != null && VfsUtilCore.isAncestor(extSrc, PsiUtilCore.getVirtualFile(psiElement)!!, false)
    }

    override fun isSelected(): Boolean = true

    override fun setSelected(selected: Boolean) {
    }

    override fun readExternal(element: Element) {
    }

    override fun writeExternal(element: Element) {
    }
  }
  EntryPointsManagerBase.DEAD_CODE_EP_NAME.point.registerExtension(entryPoint, fixture.projectDisposable)
  initialized(inspectionFixture) {}
}
