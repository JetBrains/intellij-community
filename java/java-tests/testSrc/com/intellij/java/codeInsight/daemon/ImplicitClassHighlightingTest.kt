// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Disposer
import com.intellij.pom.java.JavaFeature
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class ImplicitClassHighlightingTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_LATEST_WITH_LATEST_JDK)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/implicitClass"
  }

  @Test
  fun testHighlightInsufficientLevel() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_20, Runnable {
      doTest()
    })
  }

  @Test
  fun testWithPackageStatement() {
    doTest()
  }

  @Test
  fun testStaticInitializer() {
    doTest()
  }

  @Test
  fun testHashCodeInMethod() {
    doTest()
  }

  @Test
  fun `testIncorrect implicit class name with spaces`() {
    doTest()
  }

  @Test
  fun testIncorrectImplicitClassName() {
    myFixture.configureByFile( "Incorrect.implicit.class.name.java")
    myFixture.checkHighlighting()
  }

  @Test
  fun testNestedReferenceHighlighting() {
    doTest()
  }

  @Test
  fun testOverrideRecord() {
    doTest()
  }

  @Test
  fun testDuplicateImplicitClass() {
    myFixture.configureByText("T.java", """
      class DuplicateImplicitClass {

      }
    """.trimIndent())
    myFixture.configureByFile(myFixture.getTestName(false) + ".java")
    val highlightings = myFixture.doHighlighting().filter { it?.description?.contains("Duplicate class") ?: false }
    assertThat(highlightings).isNotEmpty()
  }

  @Test
  fun testDuplicateImplicitClass2() {
    myFixture.configureByText("DuplicateImplicitClass2.java", """
      public static void main(String[] args) {
          System.out.println("I am an implicitly declared class");
      }
    """.trimIndent())
    myFixture.configureByText("T.java", """
      class DuplicateImplicitClass2 {}
    """.trimIndent())
    val highlightings = myFixture.doHighlighting().filter { it?.description?.contains("Duplicate class") ?: false }
    assertThat(highlightings).isNotEmpty()
  }

  @Test
  fun testImplicitIoImport25(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    withLanguageLevel(JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.standardLevel!!) {
      implicitIoImport(false)
    }
  }

  @Test
  fun testImplicitIoImport23Preview(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    withLanguageLevel(JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.minimumLevel) {
      implicitIoImport(true)
    }
  }

  private suspend fun implicitIoImport(success: Boolean) {
    myFixture.addClass("""
          package java.io;
          
          public final class IO {
            public static void println(Object obj) {}    
          }
          
          """.trimIndent())
    val psiFile = myFixture.configureByFile(if (success) "ImplicitIoImport.java" else "ImplicitIoImportFail.java")
    if (success) {
      myFixture.checkHighlighting()
    }
    readAction {
      val statement = (psiFile as PsiJavaFile).classes[0].methods[0].body!!.statements[0] as PsiExpressionStatement
      val resolveMethod = (statement.expression as PsiCallExpression).resolveMethod()
      if (success) {
        assertNotNull(resolveMethod)
      }
      else {
        assertNull(resolveMethod)
      }
    }
  }

  @Test
  fun testImplicitModuleImport25(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    withLanguageLevel(JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.standardLevel!!) {
      implicitModuleImport(true)
    }
  }

  @Test
  fun testImplicitModuleImport23Preview(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    withLanguageLevel(JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.minimumLevel) {
      implicitModuleImport(true)
    }
  }

  @Test
  fun testImplicitModuleImport24(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    withLanguageLevel(LanguageLevel.JDK_24) {
      implicitModuleImport(false)
    }
  }

  private suspend fun implicitModuleImport(success: Boolean) {
    val psiFile = myFixture.configureByFile(if (success) "ImplicitModuleImport.java" else "ImplicitModuleImportFail.java")
    myFixture.checkHighlighting()
    readAction {
      val statement = (psiFile as PsiJavaFile).classes[0].methods[0].body!!.statements[0] as PsiDeclarationStatement
      val variable = (statement.declaredElements[0] as PsiVariable)
      val variableType = variable.type
      assertNotNull(variableType)
      val psiClass = PsiUtil.resolveClassInClassTypeOnly(variableType)
      if (success) {
        assertNotNull(psiClass)
        assertEquals(CommonClassNames.JAVA_UTIL_LIST, psiClass!!.qualifiedName)
      }
      else {
        assertNull(psiClass)
      }
    }
  }

  @Test
  fun testImplicitIoImport24(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    withLanguageLevel(LanguageLevel.JDK_24) {
      implicitIoImport(false)
    }
  }

  @Test
  fun testImplicitWithPackages() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_23_PREVIEW, Runnable {
      myFixture.addClass("""
        package a.b;
        
        public final class List {
        }
        """.trimIndent())
      myFixture.configureByFile(myFixture.getTestName(false) + ".java")
      myFixture.checkHighlighting()
    })
  }

  @Test
  fun testImplicitWithStaticPackagesPackagesOverModule(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    withLanguageLevel(JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.minimumLevel) {
      myFixture.addClass("""
        package a.b;
        
        public class Other {
            public static class List {
        
            }
        }""".trimIndent())
      val psiFile = myFixture.configureByFile(myFixture.getTestName(false) + ".java")
      myFixture.checkHighlighting()
      readAction {
        val element = psiFile.findElementAt(myFixture.caretOffset)
        assertEquals("a.b.Other.List", element?.parentOfType<PsiField>()?.type.resolve()?.qualifiedName)
      }
    }
  }

  @Test
  fun testImplicitWithPackagesPackagesOverModule(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    withLanguageLevel(JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.minimumLevel) {
      myFixture.addClass("""
        package a.b;
        
        public final class List {
        }
        """.trimIndent())
      val psiFile = myFixture.configureByFile(myFixture.getTestName(false) + ".java")
      myFixture.checkHighlighting()
      readAction {
        val element = psiFile.findElementAt(myFixture.caretOffset)
        assertEquals("a.b.List", element?.parentOfType<PsiField>()?.type.resolve()?.qualifiedName)
      }
    }
  }

  @Test
  fun testImplicitWithPackagesPackagesOverModule25(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    withLanguageLevel(LanguageLevel.JDK_25) {
      myFixture.addClass("""
        package a.b;
        
        public final class List {
        }
        """.trimIndent())
      val psiFile = myFixture.configureByFile(myFixture.getTestName(false) + ".java")
      myFixture.checkHighlighting()
      readAction {
        val element = psiFile.findElementAt(myFixture.caretOffset)
        assertEquals("a.b.List", element?.parentOfType<PsiField>()?.type.resolve()?.qualifiedName)
      }
    }
  }

  @Test
  fun testImplicitWithPackagesPackagesOverModule24() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_24, Runnable {
      myFixture.addClass("""
        package a.b;
        
        public final class List {
        }
        """.trimIndent())
      myFixture.configureByFile(myFixture.getTestName(false) + ".java")
      myFixture.checkHighlighting()
    })
  }

  @Test
  fun testImplicitWithSingleImport(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    withLanguageLevel(JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.minimumLevel) {
      myFixture.addClass("""
        package a.b;
        
        public final class List {
        }
        """.trimIndent())
      val psiFile = myFixture.configureByFile(myFixture.getTestName(false) + ".java")
      myFixture.checkHighlighting()
      readAction {
        val element = psiFile.findElementAt(myFixture.caretOffset)
        assertEquals("a.b.List", element?.parentOfType<PsiField>()?.type.resolve()?.qualifiedName)
      }
    }
  }

  @Test
  fun testImplicitWithSamePackage(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    withLanguageLevel(JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.minimumLevel) {
      myFixture.addClass("""
        public final class List {
        }
        """.trimIndent())
      val psiFile = myFixture.configureByFile(myFixture.getTestName(false) + ".java")
      myFixture.checkHighlighting()
      readAction {
        val element = psiFile.findElementAt(myFixture.caretOffset)
        assertEquals("List", element?.parentOfType<PsiField>()?.type.resolve()?.qualifiedName)
      }
    }
  }

  @Test
  fun testImplicitClassConstructorNoParam() {
    doTest()
  }

  @Test
  fun testImplicitClassConstructorParam() {
    doTest()
  }

  @Test
  fun testBrokenFileNoHighlighting() {
    doTest()
  }

  @Test
  fun testBrokenFileNoHighlightingWithPackage() {
    doTest()
  }

  @Test
  fun testSameNameInnerClass() {
    doTest()
  }

  @Test
  fun testSameNameInnerClass21() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21_PREVIEW, Runnable {
      doTest()
    })
  }

  @Test
  fun testInheritedMembers() {
    doTest()
  }

  private fun doTest() {
    myFixture.configureByFile(myFixture.getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }

  private suspend fun withLanguageLevel(level: LanguageLevel, action: suspend () -> Unit) {
    val disposable = Disposer.newDisposable()
    try {
      IdeaTestUtil.setProjectLanguageLevel(myFixture.project, level, disposable)
      IdeaTestUtil.setModuleLanguageLevel(myFixture.module, level, disposable)
      action()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }
}