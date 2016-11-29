/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.java19modules.Java9RedundantRequiresStatementInspection
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.NonNls

/**
 * @author Pavel.Dolgov
 */
class Java9RedundantRequiresStatementTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()

    addFile("module-info.java", "module M2 { exports org.example.m2; }", ModuleDescriptor.M2)
    addFile("module-info.java", "module M4 { exports org.example.m4; }", ModuleDescriptor.M4)
    addFile("module-info.java", "module M6 { exports org.example.m6; requires public M7; }", ModuleDescriptor.M6)
    addFile("module-info.java", "module M7 { exports org.example.m7; }", ModuleDescriptor.M7)

    add("org.example.m2", "C2", ModuleDescriptor.M2, "public static void foo() {}")
    add("org.example.m4", "C4", ModuleDescriptor.M4, "public void bar() {}")
    add("org.example.m6", "C6", ModuleDescriptor.M6, "public C7 getC7() { return new C7(); }", "org.example.m7.C7")
    add("org.example.m7", "C7", ModuleDescriptor.M7)
  }

  fun testNoSourcesAtAll() {
    mainModule("module MAIN { requires M2; }")
  }

  fun testNoImportsInSources() {
    mainClass("")
    mainModule("module MAIN { requires M2; }")
  }

  fun testPackageImported() {
    mainClass("C2.foo();", "org.example.m2.*")
    mainModule("module MAIN { requires M2; }")
  }

  fun testClassImported() {
    mainClass("C2.foo();", "org.example.m2.C2")
    mainModule("module MAIN { requires M2; }")
  }

  fun testClassMembersImported() {
    mainClass("foo();", staticImports = listOf("org.example.m2.C2.*"))
    mainModule("module MAIN { requires M2; }")
  }

  fun testMethodImported() {
    mainClass("foo();", staticImports = listOf("org.example.m2.C2.foo"))
    mainModule("module MAIN { requires M2; }")
  }


  fun testRequiresManyModulesAllPackagesImported() {
    mainClass("C2.foo(); new C4().bar(); C7 v = new C6().getC7();",
              "org.example.m2.*", "org.example.m4.*", "org.example.m6.*", "org.example.m7.*")
    mainModule("module MAIN { requires M2; requires M4; requires M6; requires M7; }")
  }

  fun testRequiresManyModulesFewPackagesImported() {
    mainClass("C2.foo(); new C6().getC7();",
              "org.example.m2.*", "org.example.m6.*")
    mainModule("module MAIN { requires M2; requires M4; requires M6; requires M7; }")
  }

  fun testReexportedPackageImported() {
    mainClass("C7 v = new C7();", "org.example.m7.*")
    mainModule("module MAIN { requires M6; }")
  }

  fun _testNonexistentMethodImported() { // causes ClassCastException in PsiImportStaticStatementImpl
    mainClass("nonexistent();",
              staticImports = listOf("org.example.m2.C2.<error descr=\"Cannot resolve symbol 'nonexistent'\">nonexistent</error>"))
    mainModule("module MAIN { requires M2; }")
  }

  private fun mainModule(@Language("JAVA") @NonNls text: String) {
    addFile("module-info.java", text, ModuleDescriptor.MAIN)

    val toolWrapper = GlobalInspectionToolWrapper(Java9RedundantRequiresStatementInspection())
    val scope = AnalysisScope(project)
    val globalContext = createGlobalContextForTool(scope, project, listOf(toolWrapper))
    InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
    InspectionTestUtil.compareToolResults(globalContext, toolWrapper, true, testDataPath + getTestName(true))

    myFixture.configureFromExistingVirtualFile(myMainClassFile ?: return)
    myFixture.checkHighlighting() // make sure the imports work
  }

  private fun add(@NonNls packageName: String,
                  @NonNls className: String,
                  module: ModuleDescriptor,
                  @NonNls body: String = "",
                  vararg @NonNls imports: String) {
    val importsText = imports.map { "import ${it};" }.joinToString("\n")
    addFile("${packageName.replace('.', '/')}/${className}.java", """
package ${packageName};
${importsText}
public class ${className}
{
  ${body}
}""", module = module)
  }

  private var myMainClassFile: VirtualFile? = null

  private fun mainClass(@NonNls body: String,
                        vararg @NonNls imports: String,
                        @NonNls staticImports: List<String> = emptyList()) {
    val importsText = imports.map { "import ${it};" }.joinToString("\n")
    val staticImportsText = staticImports.map { "import static ${it};" }.joinToString("\n")
    myMainClassFile = addFile("org.example.main/Main.java", """
package org.example.main;
${importsText}
${staticImportsText}
public class Main {
  void main() {
    ${body}
  }
}
""", module = ModuleDescriptor.MAIN)
  }

  override fun getTestDataPath() = PathManagerEx.getTestDataPath() + "/inspection/redundantRequiresStatement/"
}