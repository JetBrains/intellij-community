// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.codeInspection.jps.ClassOverriddenAtRuntimeInspection
import com.intellij.java.testFramework.fixtures.JavaModulesProject
import com.intellij.java.testFramework.fixtures.createJavaModule
import com.intellij.java.testFramework.fixtures.deleteChildren
import com.intellij.java.testFramework.fixtures.javaModulesCodeInsightFixture
import com.intellij.java.testFramework.fixtures.registerJavaModulesSdk
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class ClassOverriddenAtRuntimeInspectionTest {
  companion object {
    private val projectFixture = classOverriddenAtRuntimeProjectFixture(deep = false)

    @Language("JAVA")
    val A_SOURCE = """
      package my.example;
      public class A {
        public static void test() {}
        public void instanceTest() {}
      }
    """.trimIndent()
  }

  private val myFixture by classOverriddenAtRuntimeFixture(projectFixture)

  // Expected warning messages (entry names = module names from the descriptor)
  private val warningMessage =
    "Class 'my.example.A' will be loaded from 'DepModule' at runtime, which differs from compile-time resolution"

  private fun warningMessage(member: String) =
    "Member '$member' of class 'my.example.A' may call a different implementation at runtime because the class will be loaded from 'DepModule'"

  @Test
  fun testTypeDeclaration() {
    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main { 
        <warning descr="$warningMessage">A</warning> a; 
      }
      """.trimIndent())
    myFixture.testHighlighting()
  }

  @Test
  fun testNewExpression() {
    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main { 
        <warning descr="$warningMessage">A</warning> a = new <warning descr="$warningMessage">A</warning>(); 
      }
      """.trimIndent())
    myFixture.testHighlighting()
  }

  @Test
  fun testStaticMethodCall() {
    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main {
        void run() { 
          <warning descr="$warningMessage">A</warning>.test(); 
        }
      }
      """.trimIndent())
    myFixture.testHighlighting()
  }

  @Test
  fun testStaticMemberImport() {
    myFixture.configureByText("Main.java", """
      import static <warning descr="$warningMessage">my.example.A</warning>.test;
      public class Main {
        void run() { test(); }
      }
      """.trimIndent())
    myFixture.testHighlighting()
  }

  @Test
  fun testStaticWildcardImport() {
    myFixture.configureByText("Main.java", """
      import static <warning descr="$warningMessage">my.example.A</warning>.*;
      public class Main {
        void run() { test(); }
      }
      """.trimIndent())
    myFixture.testHighlighting()
  }

  @Test
  fun testInstanceMethodOnTypedVariable() {
    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main {
        void run(<warning descr="$warningMessage">A</warning> a) { 
          a.<warning descr="${warningMessage("instanceTest")}">instanceTest</warning>(); 
        }
      }
      """.trimIndent())
    myFixture.testHighlighting()
  }

  /** Lambda param type is inferred from {@code AConsumer} — no explicit {@code A} at the call site. */
  @Test
  fun testLambdaWithInferredType() {
    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main {
        interface AConsumer { void consume(<warning descr="$warningMessage">A</warning> a); }
        static void exec(AConsumer c) { c.consume(null); }
        void run() { 
          exec(a -> a.<warning descr="${warningMessage("instanceTest")}">instanceTest</warning>()); 
        }
      }
      """.trimIndent())
    myFixture.testHighlighting()
  }

  @Test
  fun testChainedCall() {
    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main {
        static <warning descr="$warningMessage">A</warning> getA() { return null; }
        void run() { 
          getA().<warning descr="${warningMessage("instanceTest")}">instanceTest</warning>(); 
        }
      }
      """.trimIndent())
    myFixture.testHighlighting()
  }

  @Test
  fun testMultipleUsages() {
    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main {
        <warning descr="$warningMessage">A</warning> field;
        void m(<warning descr="$warningMessage">A</warning> arg) { 
          <warning descr="$warningMessage">A</warning> local = new <warning descr="$warningMessage">A</warning>(); 
          <warning descr="$warningMessage">A</warning>.test(); 
        }
      }
      """.trimIndent())
    myFixture.testHighlighting()
  }

  @Test
  fun testNoWarningForWildcardImport() {
    myFixture.configureByText("Main.java", """
      import my.example.*;
      public class Main {}
      """.trimIndent())
    myFixture.testHighlighting()
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Deeper module hierarchy:  MainModule → [DepModule → TransModule → Av1Module, Av2Module]
// Verifies that the 3-level transitive chain is detected via DepModule.runtimeScope.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Three-level hierarchy:
 * <pre>
 *   MainModule
 *     -> DepModule (COMPILE)
 *         -> TransModule (COMPILE, not exported)
 *             -> Av1Module (COMPILE, not exported)  — provides shadowing A.java
 *     -> Av2Module (COMPILE)                        — provides compile-visible A.java
 * </pre>
 */
@TestApplication
class ClassOverriddenAtRuntimeDeepTest {
  companion object {
    private val projectFixture = classOverriddenAtRuntimeProjectFixture(deep = true)
  }

  private val myFixture by classOverriddenAtRuntimeFixture(projectFixture)

  @Test
  fun testShadowingDetectedInDeepHierarchy() {
    val warn =
      "Class 'my.example.A' will be loaded from 'DepModule' at runtime, which differs from compile-time resolution"
    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main { <warning descr="$warn">A</warning> a; }
      """.trimIndent())
    myFixture.testHighlighting()
  }

  @Test
  fun testMemberShadowingDetectedInDeepHierarchy() {
    val classWarn =
      "Class 'my.example.A' will be loaded from 'DepModule' at runtime, which differs from compile-time resolution"
    val memberWarn =
      "Member 'instanceTest' of class 'my.example.A' may call a different implementation at runtime because the class will be loaded from 'DepModule'"
    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main {
        void run(<warning descr="$classWarn">A</warning> a) {
          a.<warning descr="$memberWarn">instanceTest</warning>();
        }
      }
      """.trimIndent())
    myFixture.testHighlighting()
  }
}

/**
 * The project of [classOverriddenAtRuntimeProjectFixture].
 * [av1SourceRoot] and [av2SourceRoot] are the source roots of the modules that provide `my.example.A`.
 */
internal class ClassOverriddenAtRuntimeProject(
  project: Project,
  module: Module,
  sourceRoot: Path,
  val av1SourceRoot: Path,
  val av2SourceRoot: Path,
) : JavaModulesProject(project, module, sourceRoot)

/**
 * Creates the project of the [ClassOverriddenAtRuntimeInspection] tests for all the tests of a class. Declare it in a companion object.
 *
 * Correct module hierarchy mirroring the real-world shadowing scenario:
 * <pre>
 *   MainModule
 *     -> DepModule (COMPILE)                    — intermediate, no A.java itself
 *         -> Av1Module [COMPILE, not exported]  — provides Av1's A.java, first in DFS
 *     -> Av2Module (COMPILE)                    — provides Av2's A.java, visible at compile time
 * </pre>
 *
 * PSI resolves A from Av2Module (Av1Module is not exported -> not in MainModule compile scope).
 * DFS runtime order: DepModule first -> recurses into Av1Module -> finds A first -> shadowing.
 *
 * With [deep], DepModule depends on TransModule, and TransModule depends on DeepAv1Module.
 * The module that provides the compile-visible A.java is DeepAv2Module.
 *
 * The main module is `light_idea_test_case` with the `src` source root. Each module has Mock JDK 1.7.
 * The main module has the highest language level, and the other modules have Java 8.
 */
internal fun classOverriddenAtRuntimeProjectFixture(deep: Boolean): TestFixture<ClassOverriddenAtRuntimeProject> =
  testFixture("classOverriddenAtRuntimeProjectFixture") {
    val pathFixture = tempPathFixture()
    val project = projectFixture(pathFixture, openAfterCreation = true).init()
    val root = pathFixture.init()
    val sdk = IdeaTestUtil.getMockJdk17()
    val sdkDisposable = Disposer.newDisposable("classOverriddenAtRuntimeProjectFixture SDK")
    registerJavaModulesSdk(sdk, sdkDisposable)
    val prefix = if (deep) "deep_" else ""
    val modulePrefix = if (deep) "Deep" else ""
    val sourceRoot = root.resolve("src")
    val av1SourceRoot = root.resolve("${prefix}av1_src")
    val av2SourceRoot = root.resolve("${prefix}av2_src")
    val main = createJavaModule(project, root.resolve(LightProjectDescriptor.TEST_MODULE_NAME + ".iml"), sdk, LanguageLevel.HIGHEST,
                                listOf(sourceRoot to JavaSourceRootType.SOURCE))
    val av1Module = createJavaModule(project, root.resolve("${modulePrefix}Av1Module.iml"), sdk, LanguageLevel.JDK_1_8,
                                     listOf(av1SourceRoot to JavaSourceRootType.SOURCE))
    val depModule = createJavaModule(project, root.resolve("DepModule.iml"), sdk, LanguageLevel.JDK_1_8)
    if (deep) {
      val transModule = createJavaModule(project, root.resolve("TransModule.iml"), sdk, LanguageLevel.JDK_1_8)
      ModuleRootModificationUtil.addDependency(transModule, av1Module, DependencyScope.COMPILE, false)
      ModuleRootModificationUtil.addDependency(depModule, transModule, DependencyScope.COMPILE, false)
    }
    else {
      ModuleRootModificationUtil.addDependency(depModule, av1Module, DependencyScope.COMPILE, false)
    }
    val av2Module = createJavaModule(project, root.resolve("${modulePrefix}Av2Module.iml"), sdk, LanguageLevel.JDK_1_8,
                                     listOf(av2SourceRoot to JavaSourceRootType.SOURCE))
    ModuleRootModificationUtil.addDependency(main, depModule)
    ModuleRootModificationUtil.addDependency(main, av2Module)
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    initialized(ClassOverriddenAtRuntimeProject(project, main, sourceRoot, av1SourceRoot, av2SourceRoot)) {
      withContext(Dispatchers.EDT) { Disposer.dispose(sdkDisposable) }
    }
  }

/**
 * Creates the code insight fixture of one [ClassOverriddenAtRuntimeInspection] test. Declare it as an instance field.
 *
 * Before the test, the fixture enables the inspection and creates `my/example/A.java` in both source roots that provide it.
 * The source files are recreated for each test, so the tests stay independent. After the test, the fixture deletes them.
 */
internal fun classOverriddenAtRuntimeFixture(
  projectFixture: TestFixture<ClassOverriddenAtRuntimeProject>,
): TestFixture<JavaCodeInsightTestFixture> = testFixture("classOverriddenAtRuntimeFixture") {
  val project = projectFixture.init()
  val fixture = javaModulesCodeInsightFixture(projectFixture, "").init()
  fixture.enableInspections(ClassOverriddenAtRuntimeInspection::class.java)
  for (sourceRoot in listOf(project.av1SourceRoot, project.av2SourceRoot)) {
    val directory = withContext(Dispatchers.IO) { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(sourceRoot)!! }
    VfsTestUtil.createFile(directory, "my/example/A.java", ClassOverriddenAtRuntimeInspectionTest.A_SOURCE)
  }
  IndexingTestUtil.suspendUntilIndexesAreReady(project.project)
  initialized(fixture) {
    deleteChildren(project.project, project.av1SourceRoot)
    deleteChildren(project.project, project.av2SourceRoot)
  }
}

/**
 * Puts DepModule before Av2Module in the dependencies of the main module again, after a test applied the quick fix.
 */
internal fun resetClassOverriddenAtRuntimeOrder(project: Project) {
  val main = ModuleManager.getInstance(project).findModuleByName(LightProjectDescriptor.TEST_MODULE_NAME) ?: return
  ModuleRootModificationUtil.updateModel(main) { model ->
    val entries = model.orderEntries.toMutableList()
    val depIdx = entries.indexOfFirst { it is ModuleOrderEntry && it.moduleName == "DepModule" }
    val av2Idx = entries.indexOfFirst { it is ModuleOrderEntry && it.moduleName == "Av2Module" }
    if (depIdx >= 0 && av2Idx >= 0 && depIdx > av2Idx) {
      val dep = entries.removeAt(depIdx)
      entries.add(av2Idx, dep)
      model.rearrangeOrderEntries(entries.toTypedArray())
    }
  }
}
