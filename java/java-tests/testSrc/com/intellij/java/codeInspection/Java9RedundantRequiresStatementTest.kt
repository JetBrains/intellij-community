// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.java19modules.Java9RedundantRequiresStatementInspection
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.createGlobalContextForTool
import org.intellij.lang.annotations.Language

class Java9RedundantRequiresStatementTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()

    addFile("module-info.java", "module M2 { exports org.example.m2; }", ModuleDescriptor.M2)
    addFile("module-info.java", "module M4 { exports org.example.m4; }", ModuleDescriptor.M4)
    addFile("module-info.java", "module M6 { exports org.example.m6; requires transitive M7; }", ModuleDescriptor.M6)
    addFile("module-info.java", "module M7 { exports org.example.m7; }", ModuleDescriptor.M7)

    add("org.example.m2", "C2", ModuleDescriptor.M2, "public static void foo() {}")
    add("org.example.m4", "C4", ModuleDescriptor.M4, "public void bar() {}")
    add("org.example.m6", "C6", ModuleDescriptor.M6, "public C7 getC7() { return new C7(); }", "org.example.m7.C7")
    add("org.example.m7", "C7", ModuleDescriptor.M7)
  }
  
  fun testTestClassAndSrc() {
    addFile("CT.java", "class CT extends org.example.m2.C2 {}", ModuleDescriptor.M_TEST)
    addTestFile("MyTest.java", "class MyTest {}", ModuleDescriptor.M_TEST )
    mainModule("module m.src {requires M2; requires java.base;}", ModuleDescriptor.M_TEST)
  }

  fun testNoSourcesAtAll() {
    mainModule("module MAIN { requires M2; }")
  }

  fun testNoImportsInSources() {
    mainClass()
    mainModule("module MAIN { requires M2; }")
  }

  fun testPackageImported() {
    mainClass("org.example.m2.*")
    mainModule("module MAIN { requires M2; }")
  }

  fun testClassImported() {
    mainClass("org.example.m2.C2")
    mainModule("module MAIN { requires M2; }")
  }

  fun testClassMembersImported() {
    mainClass(staticImports = listOf("org.example.m2.C2.*"))
    mainModule("module MAIN { requires M2; }")
  }

  fun testMethodImported() {
    mainClass(staticImports = listOf("org.example.m2.C2.foo"))
    mainModule("module MAIN { requires M2; }")
  }

  fun testRequiresManyModulesAllPackagesImported() {
    mainClass("org.example.m2.*", "org.example.m4.*", "org.example.m6.*", "org.example.m7.*")
    mainModule("module MAIN { requires M2; requires M4; requires M6; requires M7; }")
  }

  fun testRequiresManyModulesFewPackagesImported() {
    mainClass("org.example.m2.*", "org.example.m6.*")
    mainModule("module MAIN { requires M2; requires M4; requires M6; requires M7; }")
  }

  fun testReexportedPackageImported() {
    mainClass("org.example.m7.*")
    mainModule("module MAIN { requires M6; }")
  }

  fun testReexportedPackageImportedTransitive() {
    mainClass("org.example.m7.*")
    mainModule("module MAIN { requires transitive M6; }")
  }

  fun testNonexistentMethodImported() {
    mainClass(staticImports = listOf("org.example.m2.C2.<error descr=\"Cannot resolve symbol 'nonexistent'\">nonexistent</error>"))
    mainModule("module MAIN { requires M2; }")
  }

  fun testRequiresJavaBase() {
    mainClass("java.util.List")
    mainModule("module MAIN { requires java.base; }")
  }

  fun testSuppressionByComment() {
    mainClass()
    mainModule("module M {\n //noinspection Java9RedundantRequiresStatement\n requires M2;\n}")
  }

  fun testSuppressionByAnnotation() {
    mainClass()
    mainModule("@SuppressWarnings(\"Java9RedundantRequiresStatement\") module M { requires M2; }")
  }

  private fun mainModule(@Language("JAVA") text: String,
                         moduleDescriptor: ModuleDescriptor = ModuleDescriptor.MAIN) {
    addFile("module-info.java", text, moduleDescriptor)

    val mainFile = myMainFile
    if (mainFile != null) {
      myFixture.configureFromExistingVirtualFile(mainFile)
      myFixture.checkHighlighting() // Sanity check: make sure the imports work (or don't work) as expected
    }

    val toolWrapper = GlobalInspectionToolWrapper(Java9RedundantRequiresStatementInspection())
    val scope = AnalysisScope(project)
    val globalContext = createGlobalContextForTool(scope, project, listOf(toolWrapper))
    InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
    InspectionTestUtil.compareToolResults(globalContext, toolWrapper, true, testDataPath + getTestName(true))
  }

  private fun add(packageName: String, className: String, module: ModuleDescriptor, body: String = "", vararg imports: String) {
    val importsText = imports.joinToString("\n") { "import ${it};" }
    addFile("${packageName.replace('.', '/')}/${className}.java", """
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
    myMainFile = addFile("org.example.main/Main.java", mainText)
  }

  override fun getTestDataPath() = PathManagerEx.getTestDataPath() + "/inspection/redundantRequiresStatement/"
}