/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.JavaLineMarkerProvider.ServiceNavigationHandler
import com.intellij.icons.AllIcons
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class ServiceLineMarkerTest : LightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_9

  override fun setUp() {
    super.setUp()

    myFixture.addFileToProject("foo/bar/MyService.java",
                               "package foo.bar; public class MyService { void doWork(); }")
  }

  fun testProvidesAsSubclass() =
    doTestImplementer("public class <caret>MyServiceImpl implements MyService {\n" +
                      "    @Override public void doWork() {}\n" +
                      "}")

  fun testProvidesWithMethod() =
    doTestImplementer("public class MyServiceImpl {\n" +
                      "public static MyService <caret>provider() {\n" +
                      "    return new MyService() { @Override public void doWork() {} };\n" +
                      "}")

  fun testLoadWithLiteral() =
    doTestLoader("void foo() {\n" +
                 "    ServiceLoader<MyService> loader = <caret>ServiceLoader.load(MyService.class);\n" +
                 "}")

  fun testLoadWithVariable() =
    doTestLoader("void foo() {\n" +
                 "    Class<MyService> service = MyService.class;\n" +
                 "    ServiceLoader<MyService> loader = <caret>ServiceLoader.load(service);\n" +
                 "}")

  fun testLoadWithClassForName() =
    doTestLoader("void foo() throws ClassNotFoundException {\n" +
                 "    ServiceLoader<MyService> loader = \n" +
                 "        <caret>ServiceLoader.load(Class.forName(\"foo.bar.MyService\"), Main.class.getClassLoader());\n" +
                 "}")

  fun testLoadWithConstant() =
    doTestLoader("static final Class<MyService> SERVICE = MyService.class;\n" +
                 "void foo() {\n" +
                 "    ServiceLoader<MyService> loader = <caret>ServiceLoader.load(SERVICE);\n" +
                 "}")

  private fun doTestLoader(text: String) {
    val module = addModule("module foo.bar { uses foo.bar.MyService; provides foo.bar.MyService with foo.bar.impl.MyServiceImpl; }")

    addImplementer("public class <caret>MyServiceImpl implements MyService {\n" +
                   "    @Override public void doWork() {}\n" +
                   "}")

    val file = addMain(text)
    doTest(file, module, DaemonBundle.message("service.uses", "foo.bar.MyService"),
           "foo.bar.MyService", PsiUsesStatement::class.java)
  }

  private fun doTestImplementer(text: String) {
    val module = addModule("module foo.bar { provides foo.bar.MyService with foo.bar.impl.MyServiceImpl; }")

    val file = addImplementer(text)
    doTest(file, module, DaemonBundle.message("service.provides", "foo.bar.MyService"),
           "foo.bar.impl.MyServiceImpl", PsiProvidesStatement::class.java)
  }

  private fun doTest(file: PsiFile, module: PsiJavaModule, message: String, fqn: String, parentType: Class<out PsiElement>) {
    myFixture.configureFromExistingVirtualFile(file.virtualFile!!)

    val atCaret = myFixture.findGuttersAtCaret().filter { it.icon === AllIcons.Gutter.Java9Service }
    assertEquals("atCaret", 1, atCaret.size)
    val all = myFixture.findAllGutters().filter { it.icon === AllIcons.Gutter.Java9Service }
    assertEquals("all", atCaret, all)

    val mark = atCaret[0]
    assertEquals(message, mark.tooltipText)

    val handler = (mark as LineMarkerInfo.LineMarkerGutterIconRenderer<*>).lineMarkerInfo.navigationHandler as ServiceNavigationHandler
    val targetReference = handler.findTargetReference(module)
    assertNotNull("targetReference", targetReference)

    val parent = PsiTreeUtil.getParentOfType(targetReference, parentType)
    UsefulTestCase.assertInstanceOf(parent, parentType)

    assertTrue("isAncestor", PsiTreeUtil.isAncestor(module, targetReference, true))
    assertEquals(fqn, targetReference.qualifiedName)
  }

  private fun addModule(text: String): PsiJavaModule =
    (myFixture.addFileToProject("module-info.java", text) as PsiJavaFile).moduleDeclaration!!

  private fun addImplementer(text: String) = myFixture.addFileToProject("foo/bar/impl/MyServiceImpl.java",
                                                                        "package foo.bar.impl; import foo.bar.MyService; $text")

  private fun addMain(method: String) = myFixture.addFileToProject("foo/bar/main/Main.java",
                                                                   "package foo.bar.main;\n" +
                                                                   "import foo.bar.MyService;\n" +
                                                                   "import java.util.ServiceLoader;\n" +
                                                                   "public class Main { $method }")
}