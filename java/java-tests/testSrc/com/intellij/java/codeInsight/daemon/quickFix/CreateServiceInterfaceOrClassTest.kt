/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.codeInsight.daemon.quickFix

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.codeInsight.daemon.impl.quickfix.CreateServiceClassFixBase
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager

/**
 * @author Pavel.Dolgov
 */
class CreateServiceInterfaceOrClassTest : LightJava9ModulesCodeInsightFixtureTestCase() {

  fun testSameModuleExistingPackage() {
    addFile("foo/bar/Anything.java", "package foo.bar; class Anything { }")

    val moduleInfo = myFixture.configureByText("module-info.java", "module foo.bar { uses foo.bar.<caret>MyService; }") as PsiJavaFile
    doAction("foo.bar.MyService", moduleInfo)

    myFixture.checkResult("foo/bar/MyService.java",
                          "package foo.bar;\n\n" +
                          "public class MyService {\n" +
                          "}", true)
  }

  fun testSameModuleExistingPackageInterface() {
    addFile("foo/bar/Anything.java", "package foo.bar; class Anything { }")

    val moduleInfo = myFixture.configureByText("module-info.java", "module foo.bar { uses foo.bar.<caret>MyService; }") as PsiJavaFile
    doAction("foo.bar.MyService", moduleInfo, isClass = false)

    myFixture.checkResult("foo/bar/MyService.java",
                          "package foo.bar;\n\n" +
                          "public interface MyService {\n" +
                          "}", true)
  }

  fun testOtherModuleExistingPackage() {
    val otherModuleInfo = moduleInfo("module foo.bar.other { exports foo.bar.other; }", OTHER)
      .let { PsiManager.getInstance(project).findFile(it) as PsiJavaFile }
    addFile("foo/bar/other/Anything.java", "package foo.bar.other; class Anything { }", OTHER)

    myFixture.configureByText("module-info.java", "module foo.bar { requires foo.bar.other; uses foo.bar.other.<caret>MyService; }")
    doAction("foo.bar.other.MyService", otherModuleInfo, otherModuleInfo.containingDirectory)

    myFixture.checkResult("../${OTHER.rootName}/foo/bar/other/MyService.java",
                          "package foo.bar.other;\n\n" +
                          "public class MyService {\n" +
                          "}", true)
  }

  fun testSameModuleExistingOuterClass() {
    addFile("foo/bar/Outer.java", "package foo.bar; public class Outer { }")

    val moduleInfo = myFixture.configureByText("module-info.java", "module foo.bar { uses foo.bar.Outer.<caret>MyService; }") as PsiJavaFile
    doAction("foo.bar.Outer.MyService", moduleInfo)

    myFixture.checkResult("foo/bar/Outer.java",
                          "package foo.bar; public class Outer {\n" +
                          "    public static class MyService {\n" +
                          "    }\n" +
                          "}", true)
  }

  fun testSameModuleExistingParentPackage() {
    addFile("foo/bar/Anything.java", "package foo.bar; class Anything { }")

    val moduleInfo = myFixture.configureByText("module-info.java", "module foo.bar { uses foo.bar.<caret>api.MyService; }") as PsiJavaFile
    doAction("foo.bar.api.MyService", moduleInfo)

    myFixture.checkResult("foo/bar/api/MyService.java",
                          "package foo.bar.api;\n\n" +
                          "public class MyService {\n" +
                          "}", true)
  }

  fun testExistingLibraryPackage() = doTestNoAction("module foo.bar { uses java.io.<caret>MyService; }")

  fun testExistingLibraryOuterClass() = doTestNoAction("module foo.bar { uses java.io.File.<caret>MyService; }")

  private fun doAction(interfaceFQN: String, moduleInfo: PsiJavaFile,
                       rootDirectory: PsiDirectory? = null, isClass: Boolean = true) {
    file.putUserData(CreateServiceClassFixBase.SERVICE_ROOT_DIR, rootDirectory ?: file.containingDirectory)
    file.putUserData(CreateServiceClassFixBase.SERVICE_IS_CLASS, isClass)

    val action = myFixture.findSingleIntention("Create interface or class '$interfaceFQN'")
    myFixture.launchAction(action)
    myFixture.checkHighlighting(false, false, false) // no error
    val serviceImpl = myFixture.findClass(interfaceFQN)
    val serviceModule = JavaModuleGraphUtil.findDescriptorByElement(serviceImpl)
    assertEquals(moduleInfo.moduleDeclaration!!, serviceModule)
  }

  private fun doTestNoAction(text: String) {
    myFixture.configureByText("module-info.java", text)
    val filtered = myFixture.availableIntentions.filter { it.text.startsWith("Create interface or class") }
    assertEquals(listOf<IntentionAction>(), filtered)
  }

  private val OTHER = MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2
}