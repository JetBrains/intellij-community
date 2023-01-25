// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager

class CreateServiceInterfaceOrClassTest : LightJava9ModulesCodeInsightFixtureTestCase() {

  fun testSameModuleExistingPackage() {
    addFile("foo/bar/Anything.java", "package foo.bar; class Anything { }")

    val moduleInfo = myFixture.configureByText("module-info.java", "module foo.bar { uses foo.bar.<caret>MyService; }") as PsiJavaFile
    doAction("foo.bar.MyService", moduleInfo)

    myFixture.checkResult("foo/bar/MyService.java",
                          "package foo.bar;\n\n" +
                          "public class MyService {\n" +
                          "}\n", true)
  }

  fun testSameModuleExistingPackageInterface() {
    addFile("foo/bar/Anything.java", "package foo.bar; class Anything { }")

    val moduleInfo = myFixture.configureByText("module-info.java", "module foo.bar { uses foo.bar.<caret>MyService; }") as PsiJavaFile
    doAction("foo.bar.MyService", moduleInfo, classKind = CreateClassKind.INTERFACE)

    myFixture.checkResult("foo/bar/MyService.java",
                          "package foo.bar;\n\n" +
                          "public interface MyService {\n" +
                          "}\n", true)
  }

  fun testSameModuleExistingPackageAnnotation() {
    addFile("foo/bar/Anything.java", "package foo.bar; class Anything { }")

    val moduleInfo = myFixture.configureByText("module-info.java", "module foo.bar { uses foo.bar.<caret>MyService; }") as PsiJavaFile
    doAction("foo.bar.MyService", moduleInfo, classKind = CreateClassKind.ANNOTATION)

    myFixture.checkResult("foo/bar/MyService.java",
                          "package foo.bar;\n\n" +
                          "public @interface MyService {\n" +
                          "}\n", true)
  }

  fun testOtherModuleExistingPackage() {
    val otherModuleInfo = moduleInfo("module foo.bar.other { exports foo.bar.other; }", OTHER)
      .let { PsiManager.getInstance(project).findFile(it) as PsiJavaFile }
    addFile("foo/bar/other/Anything.java", "package foo.bar.other; class Anything { }", OTHER)

    myFixture.configureByText("module-info.java", "module foo.bar { requires foo.bar.other; uses foo.bar.other.<caret>MyService; }")
    doAction("foo.bar.other.MyService", otherModuleInfo, otherModuleInfo.containingDirectory)

    myFixture.checkResult("../${OTHER.sourceRootName}/foo/bar/other/MyService.java",
                          "package foo.bar.other;\n\n" +
                          "public class MyService {\n" +
                          "}\n", true)
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
                          "}\n", true)
  }

  fun testExistingLibraryPackage() = doTestNoAction("module foo.bar { uses java.io.<caret>MyService; }")

  fun testExistingLibraryOuterClass() = doTestNoAction("module foo.bar { uses java.io.File.<caret>MyService; }")

  private fun doAction(interfaceFQN: String, moduleInfo: PsiJavaFile,
                       rootDirectory: PsiDirectory? = null, classKind: CreateClassKind = CreateClassKind.CLASS) {
    file.putUserData(CreateServiceClassFixBase.SERVICE_ROOT_DIR, rootDirectory ?: file.containingDirectory)
    file.putUserData(CreateServiceClassFixBase.SERVICE_CLASS_KIND, classKind)

    val action = myFixture.findSingleIntention("Create service '$interfaceFQN'")
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
