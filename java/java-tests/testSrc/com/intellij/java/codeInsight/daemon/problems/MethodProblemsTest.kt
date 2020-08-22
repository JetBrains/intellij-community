// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.codeInsight.daemon.problems.pass.ProjectProblemUtils
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.siyeh.ig.psiutils.TypeUtils

internal class MethodProblemsTest : ProjectProblemsViewTest() {

  fun testRename() = doMethodTest { method, factory ->
    method.nameIdentifier?.replace(factory.createIdentifier("bar"))
  }

  fun testChangeReturnType() = doMethodTest { method, factory ->
    method.returnTypeElement?.replace(factory.createTypeElement(PsiPrimitiveType.BOOLEAN))
  }

  fun testMakeMethodPackagePrivate() = doMethodTest { method, _ ->
    method.modifierList.setModifierProperty(PsiModifier.PUBLIC, false)
  }

  fun testMakeMethodPrivate() = doMethodTest { method, _ ->
    method.modifierList.setModifierProperty(PsiModifier.PUBLIC, false)
    method.modifierList.setModifierProperty(PsiModifier.PRIVATE, true)
  }

  fun testRemoveParameter() = doMethodTest { method, _ ->
    method.parameterList.getParameter(0)?.delete()
  }

  fun testAddParameter() = doMethodTest { method, factory ->
    val parameter = factory.createParameter("i", PsiPrimitiveType.INT)
    method.parameterList.add(parameter)
  }

  fun testChangeParameterType() = doMethodTest { method, factory ->
    method.parameterList.getParameter(0)?.typeElement?.replace(factory.createTypeElement(PsiPrimitiveType.BOOLEAN))
  }

  fun testMakeOverrideMethodPrivateInAbstractClass() {
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

    doTest(targetClass) {
      changeMethod(targetClass) { method, _ ->
        method.modifierList.setModifierProperty(PsiModifier.PUBLIC, false)
        method.modifierList.setModifierProperty(PsiModifier.PRIVATE, true)
      }
      assertTrue(hasReportedProblems<PsiClass>(refClass))
    }
  }

  fun testMakeAbstractInAbstractClass() {
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

    doTest(targetClass) {
      changeMethod(targetClass) { method, _ ->
        method.modifierList.setModifierProperty(PsiModifier.ABSTRACT, true)
      }
      assertTrue(hasReportedProblems<PsiDeclarationStatement>(refClass))
    }
  }

  fun testChangeReturnTypeOfAnnotationTypeMethod() {
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

    doTest(targetClass) {
      changeMethod(targetClass) { method, factory ->
        val type = factory.createTypeFromText("int[]", null)
        val newReturnType = factory.createTypeElement(type)
        method.returnTypeElement?.replace(newReturnType)
      }

      assertTrue(hasReportedProblems<PsiMethod>(refClass))
    }
  }

  fun testChangeOverrideMethodAndThenRemoveExtend() {
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

    doTest(targetClass) {
      changeMethod(targetClass) { method, factory ->
        val stringTypeElement = factory.createTypeElement(TypeUtils.getStringType(method))
        method.parameterList.parameters[0].typeElement?.replace(stringTypeElement)
      }

      assertFalse(hasReportedProblems<PsiAssignmentExpression>(refClass))

      WriteCommandAction.runWriteCommandAction(project) {
        val factory = JavaPsiFacade.getInstance(project).elementFactory
        val extendsList = factory.createReferenceList(PsiJavaCodeReferenceElement.EMPTY_ARRAY)
        targetClass.extendsList?.replace(extendsList)
      }
      myFixture.doHighlighting()

      assertTrue(hasReportedProblems<PsiAssignmentExpression>(refClass))
    }
  }

  fun testMethodOverrideScopeIsChanged() {
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

    doTest(bClass) {
      changeMethod(bClass) { method, _ ->
        method.modifierList.setModifierProperty(PsiModifier.PUBLIC, false)
      }
      assertTrue(hasReportedProblems<PsiMethod>(cClass))
      changeMethod(bClass) { method, _ ->
        method.modifierList.setModifierProperty(PsiModifier.PRIVATE, true)
      }
      // method now overrides A#foo
      assertFalse(hasReportedProblems<PsiMethod>(cClass))
    }
  }

  fun testMakeInterfaceMethodPrivate() {
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

    doTest(targetClass) {
      changeMethod(targetClass) { method, _ ->
        method.modifierList.setModifierProperty(PsiModifier.PRIVATE, true)
      }
    }

    assertTrue(hasReportedProblems<PsiDeclarationStatement>(refClass))
  }

  fun testInheritedGenericMethodClashInParameterizedClasses() {
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

    doTest(targetClass) {
      changeMethod(targetClass) { method, factory ->
        val param = method.parameterList.getParameter(0)!!
        val typeElement = param.typeElement!!
        val newTypeElement = factory.createTypeElementFromText("Map<Integer, String>", targetClass)
        typeElement.replace(newTypeElement)
      }
      assertTrue(hasReportedProblems<PsiClass>(refClass))
    }
  }

  fun testImmediateInstanceMethodCall() {
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

    doTest(aClass) {
      changeMethod(aClass) { method, _ ->
        method.parameterList.getParameter(0)?.delete()
      }
      val reportedElements = ProjectProblemUtils.getReportedProblems(
        myFixture.editor).keys
      assertSize(1, reportedElements)
      assertTrue(reportedElements.first() is PsiMethod)
    }
  }

  fun testImmediateNestedClassInstanceMethodCall() {
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

    doTest(aClass) {
      changeMethod(aClass.allInnerClasses[0]) { method, _ ->
        method.parameterList.getParameter(0)?.delete()
      }
      val reportedElements = ProjectProblemUtils.getReportedProblems(
        myFixture.editor).keys
      assertSize(1, reportedElements)
      assertTrue(reportedElements.first() is PsiMethod)
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

    doTest(targetClass) {
      changeMethod(targetClass, methodChangeAction)
      assertTrue(hasReportedProblems<PsiDeclarationStatement>(refClass))
    }
  }

  private fun changeMethod(targetClass: PsiClass, methodChangeAction: (PsiMethod, PsiElementFactory) -> Unit) {
    val methods = targetClass.methods
    assertSize(1, methods)
    val method = methods[0]

    WriteCommandAction.runWriteCommandAction(project) {
      val factory = JavaPsiFacade.getInstance(project).elementFactory
      methodChangeAction(method, factory)
    }
    myFixture.doHighlighting()
  }
}