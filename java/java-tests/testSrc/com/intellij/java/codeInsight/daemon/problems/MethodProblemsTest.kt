// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.codeInsight.daemon.problems.pass.ProjectProblemUtils
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiTypes
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.siyeh.ig.psiutils.TypeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class MethodProblemsTest {
  companion object {
    private val projectFixture = projectProblemsProjectFixture()
  }

  private val problems by projectProblemsFixture(projectFixture)
  private val myFixture get() = problems.fixture

  @Test
  fun testRename(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doMethodTest { method, factory ->
        method.nameIdentifier?.replace(factory.createIdentifier("bar"))
      }
    }
  }

  @Test
  fun testChangeReturnType(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doMethodTest { method, factory ->
        method.returnTypeElement?.replace(factory.createTypeElement(PsiTypes.booleanType()))
      }
    }
  }

  @Test
  fun testMakeMethodPackagePrivate(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doMethodTest { method, _ ->
        method.modifierList.setModifierProperty(PsiModifier.PUBLIC, false)
      }
    }
  }

  @Test
  fun testMakeMethodPrivate(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doMethodTest { method, _ ->
        method.modifierList.setModifierProperty(PsiModifier.PUBLIC, false)
        method.modifierList.setModifierProperty(PsiModifier.PRIVATE, true)
      }
    }
  }

  @Test
  fun testRemoveParameter(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doMethodTest { method, _ ->
        method.parameterList.getParameter(0)?.delete()
      }
    }
  }

  @Test
  fun testAddParameter(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doMethodTest { method, factory ->
        val parameter = factory.createParameter("i", PsiTypes.intType())
        method.parameterList.add(parameter)
      }
    }
  }

  @Test
  fun testChangeParameterType(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doMethodTest { method, factory ->
        method.parameterList.getParameter(0)?.typeElement?.replace(factory.createTypeElement(PsiTypes.booleanType()))
      }
    }
  }

  @Test
  fun testMakeOverrideMethodPrivateInAbstractClass(): Unit = timeoutRunBlocking {
    myFixture.addClass("""
      package foo;
      
      public abstract class Parent {
        public abstract int len(String s);
      }
    """.trimIndent())

    val targetClass = myFixture.addClass("""
      package foo;
      
      public abstract class A extends Parent {
      
        public int len(String s) {
          return s.length();
        }
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package foo;
      
      public class B extends A {
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeMethod(targetClass) { method, _ ->
          method.modifierList.setModifierProperty(PsiModifier.PUBLIC, false)
          method.modifierList.setModifierProperty(PsiModifier.PRIVATE, true)
        }
        assertTrue(problems.hasReportedProblems<PsiClass>(refClass))
      }
    }
  }

  @Test
  fun testMakeAbstractInAbstractClass(): Unit = timeoutRunBlocking {
    val targetClass = myFixture.addClass("""
      package foo;
      
      public abstract class A {
      
        public int len(String s) {
          return s.length();
        }
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package bar;
      
      import foo.A;
      
      public class B {
        void test() {
          int len = (new A() {}).len("foo");
        }
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeMethod(targetClass) { method, _ ->
          method.modifierList.setModifierProperty(PsiModifier.ABSTRACT, true)
        }
        assertTrue(problems.hasReportedProblems<PsiDeclarationStatement>(refClass))
      }
    }
  }

  @Test
  fun testChangeReturnTypeOfAnnotationTypeMethod(): Unit = timeoutRunBlocking {
    val targetClass = myFixture.addClass("""
      package foo;
      
      import java.lang.annotation.*;
      
      @Retention(RetentionPolicy.RUNTIME)
      @Inherited
      @Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
      public @interface BooleanString {
        String[] trueStrings();
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package foo;
      
      public class B {
      
        @BooleanString(trueStrings = {"TRUE", "Y"})
        void test() {
        }
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeMethod(targetClass) { method, factory ->
          val type = factory.createTypeFromText("int[]", null)
          val newReturnType = factory.createTypeElement(type)
          method.returnTypeElement?.replace(newReturnType)
        }

        assertTrue(problems.hasReportedProblems<PsiMethod>(refClass))
      }
    }
  }

  @Test
  fun testAddExceptionAndRemoveFromThrowsList(): Unit = timeoutRunBlocking {
    val targetClass = myFixture.addClass("""
      package foo;
      
      import java.io.IOException;

      public class A {
        public void foo() {}
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package foo;
      
      public class B {
        public void test(A a) {
          a.foo();
        }
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeMethod(targetClass) { method, factory ->
          val exceptionRef = factory.createReferenceElementByFQClassName(CommonClassNames.JAVA_LANG_EXCEPTION, method.resolveScope)
          method.throwsList.add(exceptionRef)
        }

        assertTrue(problems.hasReportedProblems<PsiMethod>(refClass))

        changeMethod(targetClass) { method, factory ->
          method.throwsList.replace(factory.createReferenceList(PsiJavaCodeReferenceElement.EMPTY_ARRAY))
        }

        assertFalse(problems.hasReportedProblems<PsiMethod>(refClass))
      }
    }
  }

  @Test
  fun testChangeOverrideMethodAndThenRemoveExtend(): Unit = timeoutRunBlocking {
    myFixture.addClass("""
      package foo;

      public class Parent {
        public int test(int i) { return 42; }
      }
    """.trimIndent())

    val targetClass = myFixture.addClass("""
      package foo;
      
      public class A extends Parent {
        @Override
        public int test(int i) { return 1437; }
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package foo;

      public class B {
        void useA() {
          int j = 0;
          A a = new A();
          j = a.test(0);
        }
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeMethod(targetClass) { method, factory ->
          val stringTypeElement = factory.createTypeElement(TypeUtils.getStringType(method))
          method.parameterList.parameters[0].typeElement?.replace(stringTypeElement)
        }

        assertFalse(problems.hasReportedProblems<PsiAssignmentExpression>(refClass))

        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          val factory = JavaPsiFacade.getInstance(myFixture.project).elementFactory
          val extendsList = factory.createReferenceList(PsiJavaCodeReferenceElement.EMPTY_ARRAY)
          targetClass.extendsList?.replace(extendsList)
        }
        myFixture.doHighlighting()

        assertTrue(problems.hasReportedProblems<PsiAssignmentExpression>(refClass))
      }
    }
  }

  @Test
  fun testMethodOverrideScopeIsChanged(): Unit = timeoutRunBlocking {
    myFixture.addClass("""
      package bar;
      
      public class A {
        public void foo() {}
      }
    """.trimIndent())

    val bClass = myFixture.addClass("""
      package bar;
      
      public class B extends A {
        @Override
        public void foo() {}
      }
    """.trimIndent())

    val cClass = myFixture.addClass("""
      package baz;
      
      import bar.B;
      
      public class C extends B {
        @Override
        public void foo() {}
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(bClass) {
        changeMethod(bClass) { method, _ ->
          method.modifierList.setModifierProperty(PsiModifier.PUBLIC, false)
        }
        assertTrue(problems.hasReportedProblems<PsiMethod>(cClass))
        changeMethod(bClass) { method, _ ->
          method.modifierList.setModifierProperty(PsiModifier.PRIVATE, true)
        }
        // method now overrides A#foo
        assertFalse(problems.hasReportedProblems<PsiMethod>(cClass))
      }
    }
  }

  @Test
  fun testMakeInterfaceMethodPrivate(): Unit = timeoutRunBlocking {
    val targetClass = myFixture.addClass("""
      package foo;
      
      public interface A {
        int test();
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package bar;
      
      import foo.*;
      
      public class B {
      
        void test(A a) { 
          int i = a.test();
        }
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeMethod(targetClass) { method, _ ->
          method.modifierList.setModifierProperty(PsiModifier.PRIVATE, true)
        }
      }

      assertTrue(problems.hasReportedProblems<PsiDeclarationStatement>(refClass))
    }
  }

  @Test
  fun testInheritedGenericMethodClashInParameterizedClasses(): Unit = timeoutRunBlocking {
    myFixture.addClass("""
      package foo;

      import java.io.Reader;
      import java.util.*;

      public abstract class Parent<T extends Reader> {
          protected void test(Map<String, String> out) {}

          protected abstract void foo(T t);
      }
    """.trimIndent())

    val targetClass = myFixture.addClass("""
      package foo;

      import java.io.Reader;
      import java.util.Map;

      public abstract class A<T extends Reader> extends Parent<T> {
          @Override
          protected void test(Map<String, String> out) {
          }
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package foo;

      import java.io.Reader;
      
      public class B {
          public static class Nested extends A {
              @Override
              protected void foo(Reader s) {
              }
          }
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeMethod(targetClass) { method, factory ->
          val param = method.parameterList.getParameter(0)!!
          val typeElement = param.typeElement!!
          val newTypeElement = factory.createTypeElementFromText("Map<Integer, String>", targetClass)
          typeElement.replace(newTypeElement)
        }
        assertTrue(problems.hasReportedProblems<PsiClass>(refClass))
      }
    }
  }

  @Test
  fun testImmediateInstanceMethodCall(): Unit = timeoutRunBlocking {
    val aClass = myFixture.addClass("""
      package bar;
      
      public class A {
        public void foo(int i) {}
      }
    """.trimIndent())

    myFixture.addClass("""
      package bar;
      
      public class Usage {
        void test() {
          new A().foo(42);
        }
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(aClass) {
        changeMethod(aClass) { method, _ ->
          method.parameterList.getParameter(0)?.delete()
        }
        val reportedElements = ProjectProblemUtils.getReportedProblems(
          myFixture.editor).keys
        assertThat(reportedElements).hasSize(1)
        assertTrue(reportedElements.first() is PsiMethod)
      }
    }
  }

  @Test
  fun testImmediateNestedClassInstanceMethodCall(): Unit = timeoutRunBlocking {
    val aClass = myFixture.addClass("""
      package bar;
      
      public class A {
        public static class Nested {
          public void foo(int i) {}
        }
      }
    """.trimIndent())

    myFixture.addClass("""
      package bar;
      
      public class Usage {
        void test() {
          (new A.Nested()).foo(42);
        }
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(aClass) {
        changeMethod(aClass.allInnerClasses[0]) { method, _ ->
          method.parameterList.getParameter(0)?.delete()
        }
        val reportedElements = ProjectProblemUtils.getReportedProblems(myFixture.editor).keys
        assertThat(reportedElements).hasSize(1)
        assertTrue(reportedElements.first() is PsiMethod)
      }
    }
  }

  @Test
  fun testIncompatibleTypeParam(): Unit = timeoutRunBlocking {
    val targetClass = myFixture.addClass("""
      public class TargetClass {
        static <T extends Integer> T id(T t) {
          return t;
        }
      }
    """.trimIndent())

    myFixture.addClass("""
      public class RefClass extends TargetClass {
        void test() {
          TargetClass.id("foo");
        }
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeMethod(targetClass) { psiMethod, _ ->
          psiMethod.modifierList.setModifierProperty(PsiModifier.PUBLIC, true)
        }

        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).hasSize(1)

        changeMethod(targetClass) { psiMethod, factory ->
          val typeParameterList = factory.createTypeParameterList()
          typeParameterList.add(factory.createTypeParameterFromText("T", psiMethod))
          psiMethod.typeParameterList?.replace(typeParameterList)
        }

        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).isEmpty()
      }
    }
  }

  @Test
  fun testRenameMethodAndRecreate(): Unit = timeoutRunBlocking {
    val targetClass = myFixture.addClass("""
      public class A {
        void m1() {}
      }
    """.trimIndent())

    myFixture.addClass("""
      public class RefClass {
        void test(A a) {
          a.m1();
        }
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeMethod(targetClass) { psiMethod, _ ->
          psiMethod.name = "m2"
        }

        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).hasSize(1)

        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          val factory = JavaPsiFacade.getInstance(myFixture.project).elementFactory
          targetClass.add(factory.createMethodFromText("void m1() {}", targetClass))
        }
        myFixture.doHighlighting()

        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).isEmpty()
      }
    }
  }

  @Test
  fun testRenameMethodCreateOneMoreAndRenameItToInitial(): Unit = timeoutRunBlocking {
    val targetClass = myFixture.addClass("""
      public class A {
        void m1() {}
      }
    """.trimIndent())

    myFixture.addClass("""
      public class RefClass {
        void test(A a) {
          a.m1();
        }
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeMethod(targetClass) { psiMethod, _ ->
          psiMethod.name = "m2"
        }

        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).hasSize(1)

        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          val factory = JavaPsiFacade.getInstance(myFixture.project).elementFactory
          targetClass.add(factory.createMethodFromText("void m3() {}", targetClass))
        }
        myFixture.doHighlighting()

        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).hasSize(1)

        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          val method = targetClass.findMethodsByName("m3", false)[0]
          method.name = "m1"
        }
        myFixture.doHighlighting()

        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).isEmpty()
      }
    }
  }


  private fun doMethodTest(methodChangeAction: (PsiMethod, PsiElementFactory) -> Unit) {

    val targetClass = myFixture.addClass("""
      package foo;
      
      public class A {
      
        public int len(String s) {
          return s.length();
        }
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package bar;
      
      import foo.A;
      
      public class B {
        void test() {
          int len = (new A()).len("foo");
        }
      }
    """.trimIndent())

    problems.doTest(targetClass) {
      changeMethod(targetClass, methodChangeAction)
      assertTrue(problems.hasReportedProblems<PsiDeclarationStatement>(refClass))
    }
  }

  private fun changeMethod(targetClass: PsiClass, methodChangeAction: (PsiMethod, PsiElementFactory) -> Unit) {
    val methods = targetClass.methods
    assertThat(methods).hasSize(1)
    val method = methods[0]

    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      val factory = JavaPsiFacade.getInstance(myFixture.project).elementFactory
      methodChangeAction(method, factory)
    }
    myFixture.doHighlighting()
  }
}