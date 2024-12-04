// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.pom.java.JavaFeature
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve

class ImplicitClassHighlightingTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = JAVA_21
  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/implicitClass"

  fun testHighlightInsufficientLevel() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_20, Runnable {
      doTest()
    })
  }

  fun testWithPackageStatement() {
    doTest()
  }

  fun testStaticInitializer() {
    doTest()
  }

  fun testHashCodeInMethod() {
    doTest()
  }

  fun `testIncorrect implicit class name with spaces`() {
    doTest()
  }

  fun testIncorrectImplicitClassName() {
    myFixture.configureByFile( "Incorrect.implicit.class.name.java")
    myFixture.checkHighlighting()
  }

  fun testNestedReferenceHighlighting() {
    doTest()
  }

  fun testOverrideRecord() {
    doTest()
  }

  fun testDuplicateImplicitClass() {
    myFixture.configureByText("T.java", """
      class DuplicateImplicitClass {

      }
    """.trimIndent())
    myFixture.configureByFile(getTestName(false) + ".java")
    val highlightings = myFixture.doHighlighting().filter { it?.description?.contains("Duplicate class") ?: false }
    UsefulTestCase.assertNotEmpty(highlightings)
  }

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
    UsefulTestCase.assertNotEmpty(highlightings)
  }

  fun testImplicitIoImport() {
    IdeaTestUtil.withLevel(module, JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.minimumLevel, Runnable {
      myFixture.addClass("""
        package java.io;
        
        public final class IO {
          public static void println(Object obj) {}    
        }
        
        """.trimIndent())
      val psiFile = myFixture.configureByFile(getTestName(false) + ".java")
      myFixture.checkHighlighting()
      val statement = (psiFile as PsiJavaFile).classes[0].methods[0].body!!.statements[0] as PsiExpressionStatement
      val resolveMethod = (statement.expression as PsiCallExpression).resolveMethod()
      assertNotNull(resolveMethod)
    })
  }

  fun testImplicitModuleImport() {
    IdeaTestUtil.withLevel(module, JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.minimumLevel, Runnable {
      val psiFile = myFixture.configureByFile(getTestName(false) + ".java")
      myFixture.checkHighlighting()
      val statement = (psiFile as PsiJavaFile).classes[0].methods[0].body!!.statements[0] as PsiDeclarationStatement
      val variable = (statement.declaredElements[0] as PsiVariable)
      val variableType = variable.type
      assertNotNull(variableType)
      val psiClass = PsiUtil.resolveClassInClassTypeOnly(variableType)
      assertNotNull(psiClass)
      assertEquals(CommonClassNames.JAVA_UTIL_LIST, psiClass!!.qualifiedName)
    })
  }

  fun testImplicitWithPackages() {
    IdeaTestUtil.withLevel(module, JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.minimumLevel, Runnable {
      myFixture.addClass("""
        package a.b;
        
        public final class List {
        }
        """.trimIndent())
      val psiFile = myFixture.configureByFile(getTestName(false) + ".java")
      myFixture.checkHighlighting()
    })
  }

  fun testImplicitWithPackagesPackagesOverModule() {
    IdeaTestUtil.withLevel(module, JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.minimumLevel, Runnable {
      myFixture.addClass("""
        package a.b;
        
        public final class List {
        }
        """.trimIndent())
      val psiFile = myFixture.configureByFile(getTestName(false) + ".java")
      myFixture.checkHighlighting()
      val element = psiFile.findElementAt(myFixture.caretOffset)
      assertEquals("a.b.List", element?.parentOfType<PsiField>()?.type.resolve()?.qualifiedName)
    })
  }


  fun testImplicitWithSingleImport() {
    IdeaTestUtil.withLevel(module, JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.minimumLevel, Runnable {
      myFixture.addClass("""
        package a.b;
        
        public final class List {
        }
        """.trimIndent())
      val psiFile = myFixture.configureByFile(getTestName(false) + ".java")
      myFixture.checkHighlighting()
      val element = psiFile.findElementAt(myFixture.caretOffset)
      assertEquals("a.b.List", element?.parentOfType<PsiField>()?.type.resolve()?.qualifiedName)
    })
  }

  fun testImplicitWithSamePackage() {
    IdeaTestUtil.withLevel(module, JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.minimumLevel, Runnable {
      myFixture.addClass("""
        public final class List {
        }
        """.trimIndent())
      val psiFile = myFixture.configureByFile(getTestName(false) + ".java")
      myFixture.checkHighlighting()
      val element = psiFile.findElementAt(myFixture.caretOffset)
      assertEquals("List", element?.parentOfType<PsiField>()?.type.resolve()?.qualifiedName)
    })
  }

  private fun doTest() {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}