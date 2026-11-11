// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.JavaServiceLineMarkerUtil.ServiceNavigationHandler
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.PsiProvidesStatement
import com.intellij.psi.PsiUsesStatement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.IconManager
import com.intellij.ui.IconTestUtil
import com.intellij.ui.icons.CoreIconManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class ServiceLineMarkerTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    // The icon loader must not use a fake empty icon.
    IconManager.activate(CoreIconManager())
    myFixture.addFileToProject("foo/bar/MyService.java", "package foo.bar;\npublic class MyService { void doWork(); }")
  }

  @AfterEach
  fun tearDown() {
    IconManager.deactivate()
    IconLoader.clearCacheInTests()
  }

  @Test
  fun testProvidesAsSubclass(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestImplementer("""
        public class <caret>MyServiceImpl implements MyService {
            @Override public void doWork() {}
        }""".trimIndent())
  }

  @Test
  fun testProvidesWithMethod(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestImplementer("""
          public class MyServiceImpl {
              public static MyService <caret>provider() {
                  return new MyService() { @Override public void doWork() {} };
              }
          }""".trimIndent())
  }

  @Test
  fun testSingleConstructorGutter(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestImplementer("""
          public class MyServiceImpl implements MyService {
              public <caret>MyServiceImpl() {}
              @Override public void doWork() {}
          }""".trimIndent())
  }

  @Test
  fun testSingleProviderGutter(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestImplementer("""
          public class MyServiceImpl implements MyService {
              public MyServiceImpl() {}
              public static MyService <caret>provider() {
                  return new MyServiceImpl();
              }
              @Override public void doWork() {}
          }""".trimIndent())
  }

  @Test
  fun testLoadWithLiteral(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestLoader("""
          void foo() {
              ServiceLoader<MyService> loader = <caret>ServiceLoader.load(MyService.class);
          }""".trimIndent())
  }

  @Test
  fun testLoadWithVariable(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestLoader("""
          void foo() {
              Class<MyService> service = MyService.class;
              ServiceLoader<MyService> loader = <caret>ServiceLoader.load(service);
          }""".trimIndent())
  }

  @Test
  fun testLoadWithClassForName(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestLoader("""
          void foo() throws ClassNotFoundException {
              ServiceLoader<MyService> loader = 
                  <caret>ServiceLoader.load(Class.forName("foo.bar.MyService"), Main.class.getClassLoader());
          }""".trimIndent())
  }

  @Test
  fun testLoadWithConstant(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestLoader("""
          static final Class<MyService> SERVICE = MyService.class;
          void foo() {
              ServiceLoader<MyService> loader = <caret>ServiceLoader.load(SERVICE);
          }""".trimIndent())
  }

  private suspend fun doTestLoader(text: String) {
    val module = addModule("module foo.bar { uses foo.bar.MyService; provides foo.bar.MyService with foo.bar.impl.MyServiceImpl; }")
    addImplementer("public class <caret>MyServiceImpl implements MyService {\n    @Override public void doWork() {}\n}")
    val file = addMain(text)
    doTest(file, module, "Uses service <a href=\"#javaClass/foo.bar.MyService\">foo.bar.MyService</a><br/><div style=\"margin-top: 5px\"><font size=\"2\">Click to navigate</font></div>",
           "foo.bar.MyService", PsiUsesStatement::class.java)
  }

  private suspend fun doTestImplementer(text: String) {
    val module = addModule("module foo.bar {\n  provides foo.bar.MyService with foo.bar.impl.MyServiceImpl;\n}")
    val file = addImplementer(text)
    doTest(file, module, "Provides service <a href=\"#javaClass/foo.bar.MyService\">foo.bar.MyService</a><br/><div style=\"margin-top: 5px\"><font size=\"2\">Click to navigate</font></div>",
           "foo.bar.impl.MyServiceImpl", PsiProvidesStatement::class.java)
  }

  private suspend fun doTest(file: PsiFile, module: PsiJavaModule, message: String, fqn: String, parentType: Class<out PsiElement>) {
    myFixture.configureFromExistingVirtualFile(file.virtualFile!!)

    val atCaret = myFixture.findGuttersAtCaret().filter { IconTestUtil.unwrapIcon(it.icon) == AllIcons.Gutter.Java9Service }
    assertThat(atCaret).hasSize(1)
    val all = myFixture.findAllGutters().filter { IconTestUtil.unwrapIcon(it.icon) == AllIcons.Gutter.Java9Service }
    assertThat(all).isEqualTo(atCaret)

    val mark = atCaret[0]
    readAction {
      assertEquals(message, mark.tooltipText)

      val handler = (mark as LineMarkerInfo.LineMarkerGutterIconRenderer<*>).lineMarkerInfo.navigationHandler as ServiceNavigationHandler
      val targetReference = handler.findTargetReference(module)
      assertNotNull(targetReference, "targetReference")
      assertNotNull(PsiTreeUtil.getParentOfType(targetReference, parentType))
      assertTrue(PsiTreeUtil.isAncestor(module, targetReference, true), "isAncestor")
      assertEquals(fqn, targetReference.qualifiedName)
    }
  }

  private suspend fun addModule(text: String): PsiJavaModule {
    val file = myFixture.addFileToProject("module-info.java", text) as PsiJavaFile
    return readAction { file.moduleDeclaration!! }
  }

  private fun addImplementer(text: String): PsiFile {
    return myFixture.addFileToProject("foo/bar/impl/MyServiceImpl.java", "package foo.bar.impl;\nimport foo.bar.MyService;\n${text}")
  }

  private fun addMain(method: String): PsiFile {
    return myFixture.addFileToProject("foo/bar/main/Main.java", """
          package foo.bar.main;
          import foo.bar.MyService;
          import java.util.ServiceLoader;
          public class Main {
          [METHOD]
          }""".trimIndent().replace("[METHOD]", method.prependIndent("    ")))
  }
}