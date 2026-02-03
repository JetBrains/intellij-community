// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
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
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve

class ImplicitClassHighlightingTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = JAVA_LATEST_WITH_LATEST_JDK
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
    assertNotEmpty(highlightings)
  }

  fun testImplicitIoImport25() {
    IdeaTestUtil.withLevel(module, JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.standardLevel!!, Runnable {
      implicitIoImport(false)
    })
  }

  fun testImplicitIoImport23Preview() {
    IdeaTestUtil.withLevel(module, JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.minimumLevel, Runnable {
      implicitIoImport(true)
    })
  }

  private fun implicitIoImport(success: Boolean) {
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
    val statement = (psiFile as PsiJavaFile).classes[0].methods[0].body!!.statements[0] as PsiExpressionStatement
    val resolveMethod = (statement.expression as PsiCallExpression).resolveMethod()
    if (success) {
      assertNotNull(resolveMethod)
    }
    else {
      assertNull(resolveMethod)
    }
  }

  fun testImplicitModuleImport25() {
    IdeaTestUtil.withLevel(module, JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.standardLevel!!, Runnable {
      implicitModuleImport(true)
    })
  }

  fun testImplicitModuleImport23Preview() {
    IdeaTestUtil.withLevel(module, JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.minimumLevel, Runnable {
      implicitModuleImport(true)
    })
  }

  fun testImplicitModuleImport24() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_24, Runnable {
      implicitModuleImport(false)
    })
  }

  private fun implicitModuleImport(success: Boolean) {
    val psiFile = myFixture.configureByFile(if (success) "ImplicitModuleImport.java" else "ImplicitModuleImportFail.java")
    myFixture.checkHighlighting()
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

  fun testImplicitIoImport24() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_24, Runnable {
      implicitIoImport(false)
    })
  }

  fun testImplicitWithPackages() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_23_PREVIEW, Runnable {
      myFixture.addClass("""
        package a.b;
        
        public final class List {
        }
        """.trimIndent())
      myFixture.configureByFile(getTestName(false) + ".java")
      myFixture.checkHighlighting()
    })
  }

  fun testImplicitWithStaticPackagesPackagesOverModule() {
    IdeaTestUtil.withLevel(module, JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.minimumLevel, Runnable {
      myFixture.addClass("""
        package a.b;
        
        public class Other {
            public static class List {
        
            }
        }""".trimIndent())
      val psiFile = myFixture.configureByFile(getTestName(false) + ".java")
      myFixture.checkHighlighting()
      val element = psiFile.findElementAt(myFixture.caretOffset)
      assertEquals("a.b.Other.List", element?.parentOfType<PsiField>()?.type.resolve()?.qualifiedName)
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

  fun testImplicitWithPackagesPackagesOverModule25() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_25, Runnable {
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


  fun testImplicitWithPackagesPackagesOverModule24() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_24, Runnable {
      myFixture.addClass("""
        package a.b;
        
        public final class List {
        }
        """.trimIndent())
      myFixture.configureByFile(getTestName(false) + ".java")
      myFixture.checkHighlighting()
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

  fun testImplicitClassConstructorNoParam() {
    doTest()
  }

  fun testImplicitClassConstructorParam() {
    doTest()
  }

  fun testBrokenFileNoHighlighting() {
    doTest()
  }

  fun testBrokenFileNoHighlightingWithPackage() {
    doTest()
  }

  fun testSameNameInnerClass() {
    doTest()
  }

  fun testSameNameInnerClass21() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21_PREVIEW, Runnable {
      doTest()
    })
  }

  fun testInheritedMembers() {
    doTest()
  }

  private fun doTest() {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}