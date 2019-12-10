// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.JavaServiceUtil.ServiceNavigationHandler
import com.intellij.icons.AllIcons
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class ServiceLineMarkerTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_9

  override fun isIconRequired() = true

  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject("foo/bar/MyService.java", "package foo.bar;\npublic class MyService { void doWork(); }")
  }

  fun testProvidesAsSubclass() =
    doTestImplementer("""
        public class <caret>MyServiceImpl implements MyService {
            @Override public void doWork() {}
        }""".trimIndent())

  fun testProvidesWithMethod() =
    doTestImplementer("""
        public class MyServiceImpl {
            public static MyService <caret>provider() {
                return new MyService() { @Override public void doWork() {} };
            }
        }""".trimIndent())

  fun testLoadWithLiteral() =
    doTestLoader("""
        void foo() {
            ServiceLoader<MyService> loader = <caret>ServiceLoader.load(MyService.class);
        }""".trimIndent())

  fun testLoadWithVariable() =
    doTestLoader("""
        void foo() {
            Class<MyService> service = MyService.class;
            ServiceLoader<MyService> loader = <caret>ServiceLoader.load(service);
        }""".trimIndent())

  fun testLoadWithClassForName() =
    doTestLoader("""
        void foo() throws ClassNotFoundException {
            ServiceLoader<MyService> loader = 
                <caret>ServiceLoader.load(Class.forName("foo.bar.MyService"), Main.class.getClassLoader());
        }""".trimIndent())

  fun testLoadWithConstant() =
    doTestLoader("""
        static final Class<MyService> SERVICE = MyService.class;
        void foo() {
            ServiceLoader<MyService> loader = <caret>ServiceLoader.load(SERVICE);
        }""".trimIndent())

  private fun doTestLoader(text: String) {
    val module = addModule("module foo.bar { uses foo.bar.MyService; provides foo.bar.MyService with foo.bar.impl.MyServiceImpl; }")
    addImplementer("public class <caret>MyServiceImpl implements MyService {\n    @Override public void doWork() {}\n}")
    val file = addMain(text)
    doTest(file, module, DaemonBundle.message("service.uses", "foo.bar.MyService"),
           "foo.bar.MyService", PsiUsesStatement::class.java)
  }

  private fun doTestImplementer(text: String) {
    val module = addModule("module foo.bar {\n  provides foo.bar.MyService with foo.bar.impl.MyServiceImpl;\n}")
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
    assertNotNull(PsiTreeUtil.getParentOfType(targetReference, parentType))
    assertTrue("isAncestor", PsiTreeUtil.isAncestor(module, targetReference, true))
    assertEquals(fqn, targetReference.qualifiedName)
  }

  private fun addModule(text: String): PsiJavaModule =
    (myFixture.addFileToProject("module-info.java", text) as PsiJavaFile).moduleDeclaration!!

  private fun addImplementer(text: String) =
    myFixture.addFileToProject("foo/bar/impl/MyServiceImpl.java", "package foo.bar.impl;\nimport foo.bar.MyService;\n${text}")

  private fun addMain(method: String) =
    myFixture.addFileToProject("foo/bar/main/Main.java", """
        package foo.bar.main;
        import foo.bar.MyService;
        import java.util.ServiceLoader;
        public class Main {
        [METHOD]
        }""".trimIndent().replace("[METHOD]", method.prependIndent("    ")))
}