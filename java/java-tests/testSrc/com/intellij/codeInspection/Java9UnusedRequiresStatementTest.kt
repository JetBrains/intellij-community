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

import com.intellij.codeInspection.java19modules.Java9NonAccessibleTypeExposedInspection
import com.intellij.codeInspection.java19modules.Java9UnusedRequiresStatementInspection
import com.intellij.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull

/**
 * @author Pavel.Dolgov
 */
class Java9UnusedRequiresStatementTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(Java9UnusedRequiresStatementInspection())

    addFile("module-info.java", "module M2 { exports org.example.m2; }", ModuleDescriptor.M2)
    addFile("module-info.java", "module M4 { exports org.example.m4; }", ModuleDescriptor.M4)
    addFile("module-info.java", "module M6 { exports org.example.m6; requires M7; }", ModuleDescriptor.M6)
    addFile("module-info.java", "module M7 { exports org.example.m7; }", ModuleDescriptor.M7)

    add("org.example.m2", "C2", ModuleDescriptor.M2)
    add("org.example.m4", "C4", ModuleDescriptor.M4)
    add("org.example.m6", "C6", ModuleDescriptor.M6)
    add("org.example.m7", "C7", ModuleDescriptor.M7)
  }

  fun test1() {
    highlight("module MAIN { requires M2; }")
  }

  fun test2() {
    addMain("import org.example.m2.*; public class Main { C2 field; }")
    highlight("module MAIN { requires M2; }")
  }

  private fun highlight(@Language("JAVA") @NonNls text: String) {
    val file = addFile("module-info.java", text, ModuleDescriptor.MAIN)
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.checkHighlighting()
  }

  private fun add(@NonNls packageName: String, @NonNls className: String, module: ModuleDescriptor) {
    addFile("${packageName.replace('.', '/')}/${className}.java",
            "package ${packageName}; public class @{className} { }",
            module = module)
  }

  private fun addMain(@Language("JAVA") @NonNls text: String) {
    addFile("org.example.main/Main.java",
            "package org.example.main; ${text}",
            module = ModuleDescriptor.MAIN)
  }

}