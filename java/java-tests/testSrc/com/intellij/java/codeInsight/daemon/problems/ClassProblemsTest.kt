// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

internal class ClassProblemsTest : ProjectProblemsViewTest() {

  fun testRename() = doClassTest { psiClass, factory ->
    psiClass.nameIdentifier?.replace(factory.createIdentifier("Bar"))
  }

  fun testMakeClassFinal() = doClassTest { psiClass, _ ->
    psiClass.modifierList?.setModifierProperty(PsiModifier.FINAL, true)
  }

  fun testMakeClassPackagePrivate() = doClassTest { psiClass, _ ->
    psiClass.modifierList?.setModifierProperty(PsiModifier.PUBLIC, false)
  }

  fun testChangeClassHierarchy() = doClassTest { psiClass, factory ->
    psiClass.extendsList?.replace(factory.createReferenceList(PsiJavaCodeReferenceElement.EMPTY_ARRAY))
  }

  fun testMakeClassInterface() {
    val targetClass = myFixture.addClass("""
        package foo;
        public class A {
        }
      """.trimIndent())
    val refClass = myFixture.addClass("""
        package bar;
        import foo.*;
        
        public class B {
          void test() { 
            A a = new A();;
          }
        }
      """.trimIndent())
    doTest(targetClass) {
      changeClass(targetClass) { psiClass, factory ->
        val classKeyword = PsiTreeUtil.getPrevSiblingOfType(psiClass.nameIdentifier, PsiKeyword::class.java)
        val interfaceKeyword = factory.createKeyword(PsiKeyword.INTERFACE)
        classKeyword?.replace(interfaceKeyword)
      }
      assertTrue(hasReportedProblems<PsiDeclarationStatement>(targetClass, refClass))
    }
  }

  fun testMakeNestedInner() {
    doNestedClassTest(true)
  }

  fun testMakeInnerNested() {
    doNestedClassTest(false)
  }

  fun testMakeClassAnnotationType() {
    var targetClass = myFixture.addClass("""
      package foo;
      
      public class A {
        
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package bar;
      
      import foo.*;
      
      public class B {
      
        void test() { 
          A a = new A();
        }
      }
    """.trimIndent())

    doTest(targetClass) {
      changeClass(targetClass) { _, factory ->
        val annoType = factory.createAnnotationType("A")
        targetClass = targetClass.replace(annoType) as PsiClass
      }
    }
    assertTrue(hasReportedProblems<PsiDeclarationStatement>(targetClass, refClass))
  }

  private fun doNestedClassTest(isStatic: Boolean) {
    val staticModifier = if (isStatic) "static" else ""
    val targetClass = myFixture.addClass("""
        package foo;
        
        public class A {
          public $staticModifier class Inner {}
        }
      """.trimIndent())

    val initializer = if (isStatic) "new A.Inner();" else "new A().new Inner()"
    val refClass = myFixture.addClass("""
        package bar;
        
        import foo.*;
        
        public class B {
        
          void test() { 
            A.Inner aInner = $initializer;
          }
        }
      """.trimIndent())

    doTest(targetClass) {
      changeClass(targetClass) { psiClass, _ ->
        val innerClass = psiClass.findInnerClassByName("Inner", false)
        innerClass?.modifierList?.setModifierProperty(PsiModifier.STATIC, !isStatic)
      }
      assertTrue(hasReportedProblems<PsiDeclarationStatement>(targetClass, refClass))
    }
  }

  private fun doClassTest(classChangeAction: (PsiClass, PsiElementFactory) -> Unit) {

    myFixture.addClass("""
      package foo;
      public class Parent {
      }
    """.trimIndent())


    val targetClass = myFixture.addClass("""
      package foo;
      
      public class A extends Parent {
        
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package bar;
      
      import foo.*;
      
      public class B {
      
        void test() { 
          Parent parent = new A() {};
        }
      }
    """.trimIndent())

    doTest(targetClass) {
      changeClass(targetClass, classChangeAction)
      assertTrue(hasReportedProblems<PsiDeclarationStatement>(targetClass, refClass))
    }
  }

  private fun changeClass(targetClass: PsiClass, classChangeAction: (PsiClass, PsiElementFactory) -> Unit) {
    WriteCommandAction.runWriteCommandAction(project) {
      val factory = JavaPsiFacade.getInstance(project).elementFactory
      classChangeAction(targetClass, factory)
    }
    myFixture.doHighlighting()
  }
}
