// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.java19api

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInspection.java19api.Java9UndeclaredServiceUsageInspection
import com.intellij.java.JavaBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_9
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class Java9UndeclaredServiceUsageTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_9)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  private val message = JavaBundle.message("inspection.undeclared.service.usage.message", "com.example.MyService")
  private val fix = QuickFixBundle.message("module.info.add.uses.name", "com.example.MyService")

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(Java9UndeclaredServiceUsageInspection())
    myFixture.addFileToProject("com/example/MyService.java", "package com.example;\npublic interface MyService {}")
  }

  @Test
  fun testServiceDeclared(): Unit = highlightDeclared("load(MyService.class)")
  @Test
  fun testServiceDeclaredCL(): Unit = highlightDeclared("load(MyService.class, getClass().getClassLoader())")
  @Test
  fun testServiceDeclaredML(): Unit = highlightDeclared("load(getClass().getModule().getLayer(), MyService.class)")
  @Test
  fun testServiceDeclaredInstalled(): Unit = highlightDeclared("loadInstalled(MyService.class)")

  @Test
  fun testServiceNotDeclared(): Unit = highlightNotDeclared("load(<MyService.class>)")
  @Test
  fun testServiceNotDeclaredCL(): Unit = highlightNotDeclared("load(<MyService.class>, getClass().getClassLoader())")
  @Test
  fun testServiceNotDeclaredML(): Unit = highlightNotDeclared("load(getClass().getModule().getLayer(), <MyService.class>)")
  @Test
  fun testServiceNotDeclaredInstalled(): Unit = highlightNotDeclared("loadInstalled(<MyService.class>)")

  @Test
  fun testFix(): Unit = timeoutRunBlocking(timeout = 2.minutes) { fix("load(<caret>MyService.class)") }
  @Test
  fun testFixCL(): Unit = timeoutRunBlocking(timeout = 2.minutes) { fix("load(<caret>MyService.class, getClass().getClassLoader())") }
  @Test
  fun testFixML(): Unit = timeoutRunBlocking(timeout = 2.minutes) { fix("load(getClass().getModule().getLayer(), <caret>MyService.class)") }
  @Test
  fun testFixInstalled(): Unit = timeoutRunBlocking(timeout = 2.minutes) { fix("loadInstalled(<caret>MyService.class)") }

  @Test
  fun testFixConstant(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    addConst("")
    fix("load(<caret>Const.CLAZZ)")
  }

  @Test
  fun testFixRawConstant(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
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

  private suspend fun fix(call: String) {
    configure(call, false)
    withContext(Dispatchers.EDT) {
      myFixture.launchAction(myFixture.filterAvailableIntentions(fix).first())
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      myFixture.checkHighlighting()  // no warning
      myFixture.checkResult("module-info.java", moduleText(true), false)
    }
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