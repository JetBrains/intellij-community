// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.java19api

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInspection.java19api.Java9UndeclaredServiceUsageInspection
import com.intellij.java.JavaBundle
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class Java9UndeclaredServiceUsageTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_9

  private val message = JavaBundle.message("inspection.undeclared.service.usage.message", "com.example.MyService")
  private val fix = QuickFixBundle.message("module.info.add.uses.name", "com.example.MyService")

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(Java9UndeclaredServiceUsageInspection())
    myFixture.addFileToProject("com/example/MyService.java", "package com.example;\npublic interface MyService {}")
  }

  fun testServiceDeclared() = highlightDeclared("load(MyService.class)")
  fun testServiceDeclaredCL() = highlightDeclared("load(MyService.class, getClass().getClassLoader())")
  fun testServiceDeclaredML() = highlightDeclared("load(getClass().getModule().getLayer(), MyService.class)")
  fun testServiceDeclaredInstalled() = highlightDeclared("loadInstalled(MyService.class)")

  fun testServiceNotDeclared() = highlightNotDeclared("load(<MyService.class>)")
  fun testServiceNotDeclaredCL() = highlightNotDeclared("load(<MyService.class>, getClass().getClassLoader())")
  fun testServiceNotDeclaredML() = highlightNotDeclared("load(getClass().getModule().getLayer(), <MyService.class>)")
  fun testServiceNotDeclaredInstalled() = highlightNotDeclared("loadInstalled(<MyService.class>)")

  fun testFix() = fix("load(<caret>MyService.class)")
  fun testFixCL() = fix("load(<caret>MyService.class, getClass().getClassLoader())")
  fun testFixML() = fix("load(getClass().getModule().getLayer(), <caret>MyService.class)")
  fun testFixInstalled() = fix("loadInstalled(<caret>MyService.class)")

  fun testFixConstant() {
    addConst("")
    fix("load(<caret>Const.CLAZZ)")
  }

  fun testFixRawConstant() {
    addConst("<MyService>")
    fix("load(<caret>Const.CLAZZ)")
  }

  private fun addConst(arg: String) =
    addFile("foo/bar/Const.java", "package foo.bar;\nimport com.example.MyService;\nclass Const { static final Class${arg} CLAZZ = MyService.class; }")

  private fun highlightDeclared(call: String) =
    highlight(call, true)

  private fun highlightNotDeclared(call: String) =
    highlight(call.replaceFirst(">", "</warning>").replaceFirst("<", "<warning descr=\"$message\">"), false)

  private fun highlight(call: String, declared: Boolean) {
    configure(call, declared)
    myFixture.checkHighlighting()
  }

  private fun fix(call: String) {
    configure(call, false)
    myFixture.launchAction(myFixture.filterAvailableIntentions(fix).first())
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkHighlighting()  // no warning
    myFixture.checkResult("module-info.java", moduleText(true), false)
  }

  private fun configure(call: String, declared: Boolean) {
    addFile("module-info.java", moduleText(declared))

    val file = addFile("foo/bar/Main.java", """
        package foo.bar;
        import com.example.MyService;
        import java.util.ServiceLoader;
        class Main {
            void main() {
                ServiceLoader<MyService> loader = ServiceLoader.${call};
            }
        }""".trimIndent())
    myFixture.configureFromExistingVirtualFile(file)
  }

  private fun moduleText(withUses: Boolean) =
    if (withUses) "module foo.bar {\n    uses com.example.MyService;\n}" else "module foo.bar {}"

  private fun addFile(path: String, text: String) = myFixture.addFileToProject(path, text).virtualFile
}