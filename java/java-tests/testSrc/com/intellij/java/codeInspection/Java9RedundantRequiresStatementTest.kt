// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.java19modules.Java9RedundantRequiresStatementInspection
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor
import com.intellij.java.testFramework.fixtures.lightJava9ModulesFixture
import com.intellij.java.testFramework.fixtures.lightJava9ModulesProjectFixture
import com.intellij.openapi.application.EDT
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class Java9RedundantRequiresStatementTest {
  companion object {
    private val projectFixture = lightJava9ModulesProjectFixture()
  }

  private val java9 by lightJava9ModulesFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/redundantRequiresStatement/")
  private val myFixture get() = java9.fixture

  @BeforeEach
  fun setUp() {
    java9.addFile("module-info.java", "module M2 { exports org.example.m2; }", ModuleDescriptor.M2)
    java9.addFile("module-info.java", "module M4 { exports org.example.m4; }", ModuleDescriptor.M4)
    java9.addFile("module-info.java", "module M6 { exports org.example.m6; requires transitive M7; }", ModuleDescriptor.M6)
    java9.addFile("module-info.java", "module M7 { exports org.example.m7; }", ModuleDescriptor.M7)

    add("org.example.m2", "C2", ModuleDescriptor.M2, "public static void foo() {}")
    add("org.example.m4", "C4", ModuleDescriptor.M4, "public void bar() {}")
    add("org.example.m6", "C6", ModuleDescriptor.M6, "public C7 getC7() { return new C7(); }", "org.example.m7.C7")
    add("org.example.m7", "C7", ModuleDescriptor.M7)
  }
  
  @Test
  fun testTestClassAndSrc(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    java9.addFile("CT.java", "class CT extends org.example.m2.C2 {}", ModuleDescriptor.M_TEST)
    java9.addTestFile("MyTest.java", "class MyTest {}", ModuleDescriptor.M_TEST )
    mainModule("module m.src {requires M2; requires java.base;}", ModuleDescriptor.M_TEST)
  }

  @Test
  fun testNoSourcesAtAll(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    mainModule("module MAIN { requires M2; }")
  }

  @Test
  fun testNoImportsInSources(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    mainClass()
    mainModule("module MAIN { requires M2; }")
  }

  @Test
  fun testPackageImported(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    mainClass("org.example.m2.*")
    mainModule("module MAIN { requires M2; }")
  }

  @Test
  fun testClassImported(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    mainClass("org.example.m2.C2")
    mainModule("module MAIN { requires M2; }")
  }

  @Test
  fun testClassMembersImported(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    mainClass(staticImports = listOf("org.example.m2.C2.*"))
    mainModule("module MAIN { requires M2; }")
  }

  @Test
  fun testMethodImported(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    mainClass(staticImports = listOf("org.example.m2.C2.foo"))
    mainModule("module MAIN { requires M2; }")
  }

  @Test
  fun testRequiresManyModulesAllPackagesImported(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    mainClass("org.example.m2.*", "org.example.m4.*", "org.example.m6.*", "org.example.m7.*")
    mainModule("module MAIN { requires M2; requires M4; requires M6; requires M7; }")
  }

  @Test
  fun testRequiresManyModulesFewPackagesImported(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    mainClass("org.example.m2.*", "org.example.m6.*")
    mainModule("module MAIN { requires M2; requires M4; requires M6; requires M7; }")
  }

  @Test
  fun testReexportedPackageImported(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    mainClass("org.example.m7.*")
    mainModule("module MAIN { requires M6; }")
  }

  @Test
  fun testReexportedPackageImportedTransitive(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    mainClass("org.example.m7.*")
    mainModule("module MAIN { requires transitive M6; }")
  }

  @Test
  fun testNonexistentMethodImported(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    mainClass(staticImports = listOf("org.example.m2.C2.<error descr=\"Cannot resolve symbol 'nonexistent'\">nonexistent</error>"))
    mainModule("module MAIN { requires M2; }")
  }

  @Test
  fun testRequiresJavaBase(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    mainClass("java.util.List")
    mainModule("module MAIN { requires java.base; }")
  }

  @Test
  fun testSuppressionByComment(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    mainClass()
    mainModule("module M {\n //noinspection Java9RedundantRequiresStatement\n requires M2;\n}")
  }

  @Test
  fun testSuppressionByAnnotation(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    mainClass()
    mainModule("@SuppressWarnings(\"Java9RedundantRequiresStatement\") module M { requires M2; }")
  }

  private suspend fun mainModule(@Language("JAVA") text: String,
                         moduleDescriptor: ModuleDescriptor = ModuleDescriptor.MAIN) {
    java9.addFile("module-info.java", text, moduleDescriptor)

    val mainFile = myMainFile
    if (mainFile != null) {
      myFixture.configureFromExistingVirtualFile(mainFile)
      myFixture.checkHighlighting() // Sanity check: make sure the imports work (or don't work) as expected
    }

    val toolWrapper = GlobalInspectionToolWrapper(Java9RedundantRequiresStatementInspection())
    val scope = AnalysisScope(myFixture.project)
    val globalContext = createGlobalContextForTool(scope, myFixture.project, listOf(toolWrapper))
    withContext(Dispatchers.EDT) {
      InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
      InspectionTestUtil.compareToolResults(globalContext, toolWrapper, true, myFixture.testDataPath + myFixture.getTestName(true))
    }
  }

  private fun add(packageName: String, className: String, module: ModuleDescriptor, body: String = "", vararg imports: String) {
    val importsText = imports.joinToString("\n") { "import ${it};" }
    java9.addFile("${packageName.replace('.', '/')}/${className}.java", """
        package ${packageName};
        ${importsText}
        public class ${className} {
          ${body}
        }""".trimIndent(), module = module)
  }

  private var myMainFile: VirtualFile? = null

  private fun mainClass(vararg imports: String, staticImports: List<String> = emptyList()) {
    val importsText = imports.joinToString("\n") { "import ${it};" }
    val staticImportsText = staticImports.joinToString("\n") { "import static ${it};" }
    val mainText = """
        package org.example.main;
        ${importsText}
        ${staticImportsText}
        public class Main {
          void main() {}
        }""".trimIndent()
    myMainFile = java9.addFile("org.example.main/Main.java", mainText)
  }
}