// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.codeInspection.jps.ClassOverriddenAtRuntimeInspection
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.jps.model.java.JavaSourceRootType

class ClassOverriddenAtRuntimeInspectionTest : LightJavaCodeInsightFixtureTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor = ClassOverriddenAtRuntimeProjectDescriptor

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ClassOverriddenAtRuntimeInspection::class.java)
    // Source files are recreated per test so tests remain independent
    VfsTestUtil.createFile(ClassOverriddenAtRuntimeProjectDescriptor.av1SourceRoot()!!, "my/example/A.java", A_SOURCE)
    VfsTestUtil.createFile(ClassOverriddenAtRuntimeProjectDescriptor.av2SourceRoot()!!, "my/example/A.java", A_SOURCE)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  override fun tearDown() {
    try {
      ClassOverriddenAtRuntimeProjectDescriptor.cleanupSourceRoots()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  // Expected warning messages (entry names = module names from the descriptor)
  private val warningMessage =
    "Class 'my.example.A' will be loaded from 'DepModule' at runtime, which differs from compile-time resolution"

  private fun warningMessage(member: String) =
    "Member '$member' of class 'my.example.A' may call a different implementation at runtime because the class will be loaded from 'DepModule'"

  fun testTypeDeclaration() {
    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main { 
        <warning descr="$warningMessage">A</warning> a; 
      }
      """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testNewExpression() {
    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main { 
        <warning descr="$warningMessage">A</warning> a = new <warning descr="$warningMessage">A</warning>(); 
      }
      """.trimIndent())
    myFixture.testHighlighting()
  }

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

  fun testStaticMemberImport() {
    myFixture.configureByText("Main.java", """
      import static <warning descr="$warningMessage">my.example.A</warning>.test;
      public class Main {
        void run() { test(); }
      }
      """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testStaticWildcardImport() {
    myFixture.configureByText("Main.java", """
      import static <warning descr="$warningMessage">my.example.A</warning>.*;
      public class Main {
        void run() { test(); }
      }
      """.trimIndent())
    myFixture.testHighlighting()
  }

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

  fun testNoWarningForWildcardImport() {
    myFixture.configureByText("Main.java", """
      import my.example.*;
      public class Main {}
      """.trimIndent())
    myFixture.testHighlighting()
  }

  companion object {
    @Language("JAVA")
    val A_SOURCE = """
      package my.example;
      public class A {
        public static void test() {}
        public void instanceTest() {}
      }
    """.trimIndent()
  }
}

/**
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
 */
object ClassOverriddenAtRuntimeProjectDescriptor : DefaultLightProjectDescriptor() {
  private const val AV1_SRC = "av1_src"
  private const val AV2_SRC = "av2_src"

  fun av1SourceRoot(): VirtualFile? = TempFileSystem.getInstance().findFileByPath("/$AV1_SRC")
  fun av2SourceRoot(): VirtualFile? = TempFileSystem.getInstance().findFileByPath("/$AV2_SRC")

  override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk17()

  override fun setUpProject(project: Project, handler: SetupHandler) {
    super.setUpProject(project, handler)

    runWriteAction {
      val main = ModuleManager.getInstance(project).findModuleByName(TEST_MODULE_NAME)!!

      // Av1Module: provides the shadowing A.java (first in DFS via DepModule)
      val av1Module = createModule(project, "${FileUtil.getTempDirectory()}/Av1Module.iml")
      ModuleRootModificationUtil.updateModel(av1Module) { model ->
        model.sdk = sdk
        val src = createSourceRoot(av1Module, AV1_SRC)
        model.addContentEntry(src).addSourceFolder(src, JavaSourceRootType.SOURCE)
        model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_1_8
      }

      // DepModule: intermediate module, depends on Av1Module (not exported to MainModule)
      val depModule = createModule(project, "${FileUtil.getTempDirectory()}/DepModule.iml")
      ModuleRootModificationUtil.updateModel(depModule) { model ->
        model.sdk = sdk
        model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_1_8
      }
      ModuleRootModificationUtil.addDependency(depModule, av1Module, DependencyScope.COMPILE, false)

      // Av2Module: provides compile-visible A.java (PSI resolves from here)
      val av2Module = createModule(project, "${FileUtil.getTempDirectory()}/Av2Module.iml")
      ModuleRootModificationUtil.updateModel(av2Module) { model ->
        model.sdk = sdk
        val src = createSourceRoot(av2Module, AV2_SRC)
        model.addContentEntry(src).addSourceFolder(src, JavaSourceRootType.SOURCE)
        model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_1_8
      }

      // MainModule -> [DepModule, Av2Module]
      ModuleRootModificationUtil.addDependency(main, depModule)
      ModuleRootModificationUtil.addDependency(main, av2Module)

      // Ensure DepModule precedes Av2Module so DFS visits Av1 before Av2
      ensureOrder(main, depModule, av2Module)
    }

    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  private fun ensureOrder(main: Module, dep: Module, av2: Module) {
    ModuleRootModificationUtil.updateModel(main) { model ->
      val entries = model.orderEntries.toMutableList()
      val depIdx = entries.indexOfFirst { it is ModuleOrderEntry && it.moduleName == dep.name }
      val av2Idx = entries.indexOfFirst { it is ModuleOrderEntry && it.moduleName == av2.name }
      if (depIdx > av2Idx && depIdx >= 0 && av2Idx >= 0) {
        val e = entries.removeAt(depIdx)
        entries.add(av2Idx, e)
        model.rearrangeOrderEntries(entries.toTypedArray())
      }
    }
  }

  fun reset(project: Project) {
    val main = ModuleManager.getInstance(project).findModuleByName(TEST_MODULE_NAME) ?: return
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

  fun cleanupSourceRoots() = runWriteAction {
    listOfNotNull(av1SourceRoot(), av2SourceRoot())
      .flatMap { it.children.toList() }
      .forEach { it.delete(this) }
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
class ClassOverriddenAtRuntimeDeepTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = ClassOverriddenAtRuntimeDeepDescriptor

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ClassOverriddenAtRuntimeInspection::class.java)
    VfsTestUtil.createFile(ClassOverriddenAtRuntimeDeepDescriptor.av1SourceRoot()!!, "my/example/A.java",
                           ClassOverriddenAtRuntimeInspectionTest.A_SOURCE)
    VfsTestUtil.createFile(ClassOverriddenAtRuntimeDeepDescriptor.av2SourceRoot()!!, "my/example/A.java",
                           ClassOverriddenAtRuntimeInspectionTest.A_SOURCE)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  override fun tearDown() {
    try {
      ClassOverriddenAtRuntimeDeepDescriptor.cleanupSourceRoots()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testShadowingDetectedInDeepHierarchy() {
    val warn =
      "Class 'my.example.A' will be loaded from 'DepModule' at runtime, which differs from compile-time resolution"
    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main { <warning descr="$warn">A</warning> a; }
      """.trimIndent())
    myFixture.testHighlighting()
  }

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

object ClassOverriddenAtRuntimeDeepDescriptor : DefaultLightProjectDescriptor() {
  private const val AV1_SRC = "deep_av1_src"
  private const val AV2_SRC = "deep_av2_src"

  fun av1SourceRoot(): VirtualFile? = TempFileSystem.getInstance().findFileByPath("/$AV1_SRC")
  fun av2SourceRoot(): VirtualFile? = TempFileSystem.getInstance().findFileByPath("/$AV2_SRC")

  override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk17()

  override fun setUpProject(project: Project, handler: SetupHandler) {
    super.setUpProject(project, handler)

    runWriteAction {
      val main = ModuleManager.getInstance(project).findModuleByName(TEST_MODULE_NAME)!!

      val av1Module = createModule(project, "${FileUtil.getTempDirectory()}/DeepAv1Module.iml")
      ModuleRootModificationUtil.updateModel(av1Module) { model ->
        model.sdk = sdk
        val src = createSourceRoot(av1Module, AV1_SRC)
        model.addContentEntry(src).addSourceFolder(src, JavaSourceRootType.SOURCE)
        model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_1_8
      }

      val transModule = createModule(project, "${FileUtil.getTempDirectory()}/TransModule.iml")
      ModuleRootModificationUtil.updateModel(transModule) { model ->
        model.sdk = sdk
        model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_1_8
      }
      ModuleRootModificationUtil.addDependency(transModule, av1Module, DependencyScope.COMPILE, false)

      val depModule = createModule(project, "${FileUtil.getTempDirectory()}/DepModule.iml")
      ModuleRootModificationUtil.updateModel(depModule) { model ->
        model.sdk = sdk
        model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_1_8
      }
      ModuleRootModificationUtil.addDependency(depModule, transModule, DependencyScope.COMPILE, false)

      val av2Module = createModule(project, "${FileUtil.getTempDirectory()}/DeepAv2Module.iml")
      ModuleRootModificationUtil.updateModel(av2Module) { model ->
        model.sdk = sdk
        val src = createSourceRoot(av2Module, AV2_SRC)
        model.addContentEntry(src).addSourceFolder(src, JavaSourceRootType.SOURCE)
        model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_1_8
      }

      ModuleRootModificationUtil.addDependency(main, depModule)
      ModuleRootModificationUtil.addDependency(main, av2Module)
      ensureOrder(main, depModule, av2Module)
    }

    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  private fun ensureOrder(main: Module, dep: Module, av2: Module) {
    ModuleRootModificationUtil.updateModel(main) { model ->
      val entries = model.orderEntries.toMutableList()
      val depIdx = entries.indexOfFirst { it is ModuleOrderEntry && it.moduleName == dep.name }
      val av2Idx = entries.indexOfFirst { it is ModuleOrderEntry && it.moduleName == av2.name }
      if (depIdx > av2Idx && depIdx >= 0 && av2Idx >= 0) {
        val e = entries.removeAt(depIdx)
        entries.add(av2Idx, e)
        model.rearrangeOrderEntries(entries.toTypedArray())
      }
    }
  }

  fun cleanupSourceRoots() = runWriteAction {
    listOfNotNull(av1SourceRoot(), av2SourceRoot())
      .flatMap { it.children.toList() }
      .forEach { it.delete(this) }
  }
}
