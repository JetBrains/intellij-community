// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.codeInsight.daemon.problems.pass.ProjectProblemPassUtils
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.*
import com.intellij.testFramework.UsefulTestCase

internal class FieldProblemsTest : ProjectProblemsViewTest() {

  fun testRenameField() = doFieldTest { field: PsiField, factory: PsiElementFactory ->
    field.nameIdentifier.replace(factory.createIdentifier("f"))
  }

  fun testChangeFieldType() = doFieldTest { field: PsiField, factory: PsiElementFactory ->
    field.typeElement?.replace(factory.createTypeElementFromText(CommonClassNames.JAVA_LANG_INTEGER, null))
  }

  fun testMakeFieldPackagePrivate() = doFieldTest { psiField, _ ->
    psiField.modifierList?.setModifierProperty(PsiModifier.PUBLIC, false)
  }

  fun testMakeFieldPrivate() = doFieldTest { psiField, _ ->
    psiField.modifierList?.setModifierProperty(PsiModifier.PUBLIC, false)
    psiField.modifierList?.setModifierProperty(PsiModifier.PRIVATE, true)
  }

  fun testMakeFieldNonStatic() = doFieldTest { psiField, _ ->
    psiField.modifierList?.setModifierProperty(PsiModifier.STATIC, false)
  }

  fun testMakeFieldFinal() = doFieldTest { psiField, _ ->
    psiField.modifierList?.setModifierProperty(PsiModifier.FINAL, true)
  }

  fun testRenameFieldAndUndo() {
    val targetClass = myFixture.addClass("""
      package foo;
      public class A {
        public static String field;
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package bar;
      import foo.A;
      public class B {
        void test() {
          String aField = A.field;
        }
      }
    """.trimIndent())

    doTest(targetClass) {

      val factory = JavaPsiFacade.getInstance(project).elementFactory

      changeField(targetClass) {
        val replacement = factory.createFieldFromText("public static String f;", targetClass)
        it.replace(replacement)
      }

      assertTrue(hasReportedProblems<PsiLocalVariable>(targetClass, refClass))

      changeField(targetClass) {
        val replacement = factory.createFieldFromText("public static String field;", targetClass)
        it.replace(replacement)
      }

      assertEmpty(getProblems(targetClass.containingFile))
    }
  }

  fun testTwoClassesWithSameFieldName() {
    val aClass = myFixture.addClass("""
      package foo;
      public class A {
        public static String mySpecialField;
      }
    """.trimIndent())

    val bClass = myFixture.addClass("""
      package foo;
      public class B {
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package foo;
      public class C {
        void test() {
          int i = new A().mySpecialField / 2;
        }
      }
    """.trimIndent())

    doTest(bClass) {
      val factory = JavaPsiFacade.getInstance(project).elementFactory
      val field = factory.createFieldFromText("public static String mySpecialField;", bClass)
      WriteCommandAction.runWriteCommandAction(project) { bClass.add(field) }
      myFixture.doHighlighting()
      assertEmpty(getProblems(bClass.containingFile))

      myFixture.openFileInEditor(aClass.containingFile.virtualFile)
      myFixture.doHighlighting()
      changeField(aClass) { it.modifierList?.setModifierProperty(PsiModifier.STATIC, false) }
      assertTrue(hasReportedProblems<PsiLocalVariable>(aClass, refClass))
    }
  }

  fun testAmbiguousReference() {
    myFixture.addClass("""
      package foo;
      public class A {
        public static String field;
      }
    """.trimIndent())

    val bClass = myFixture.addClass("""
      package bar;
      public class B {
      }
    """.trimIndent())

    val cClass = myFixture.addClass("""
      package baz;
      import static foo.A.*;
      import static bar.B.*;
      public class C {
        void test() {
          String aField = field;
        }
      }
    """.trimIndent())

    doTest(bClass) {
      val factory = JavaPsiFacade.getInstance(project).elementFactory
      val field = factory.createFieldFromText("public static String field;", bClass)
      WriteCommandAction.runWriteCommandAction(project) {
        bClass.add(field)
      }
      myFixture.doHighlighting()
      assertTrue(hasReportedProblems<PsiLocalVariable>(bClass, cClass))
    }
  }

  fun testTooManyUsages() {
    val targetClass = myFixture.addClass("""
      package foo;
      public class A {
        public static String field;
      }
    """.trimIndent())

    val refClassText = """
      package bar;
      import foo.A;
      public class classname {
        void test() {
          String field = A.field;
        }
      }
    """.trimIndent()

    val nRefClasses = 3
    val refClasses = sequenceOf(0, 1, 2)
      .map { myFixture.addClass(refClassText.replace("classname", "RefClass$it")) }
      .toList()

    // current file is also checked
    Registry.get("ide.unused.symbol.calculation.maxFilesToSearchUsagesIn").setValue(nRefClasses + 1 + 1)

    doTest(targetClass) {
      val factory = JavaPsiFacade.getInstance(project).elementFactory

      changeField(targetClass) { it.nameIdentifier.replace(factory.createIdentifier("f")) }
      assertTrue(hasReportedProblems<PsiLocalVariable>(targetClass, *refClasses.toTypedArray()))

      changeField(targetClass) { it.nameIdentifier.replace(factory.createIdentifier("field")) }
      val psiFile = targetClass.containingFile
      assertEmpty(getProblems(psiFile))

      myFixture.addClass(refClassText.replace("classname", "RefClass$nRefClasses"))

      changeField(targetClass) { it.nameIdentifier.replace(factory.createIdentifier("f")) }
      assertEmpty(getProblems(psiFile))
    }
  }

  fun testErrorsRemovedAfterScopeChanged() {
    val targetClass = myFixture.addClass("""
      package foo;
      public class A {
      }
    """.trimIndent())

    val packageRefClass = myFixture.addClass("""
      package foo;
      public class RefClass {
        void test() {
          A.field = "bar";
        }
      }
    """.trimIndent())

    Registry.get("ide.unused.symbol.calculation.maxFilesToSearchUsagesIn").setValue(3)

    doTest(targetClass) {

      val outsideRefClass = myFixture.addClass("""
      package bar;

      import foo.A;

      public class RefClass1 {
        void test() {
          System.out.println(A.field);
        }
      }
    """.trimIndent())

      val factory = JavaPsiFacade.getElementFactory(project)
      WriteCommandAction.runWriteCommandAction(project) {
        val psiField = factory.createFieldFromText("static final String field = \"foo\";", null)
        targetClass.add(psiField)
      }
      myFixture.doHighlighting()

      assertFalse(hasReportedProblems<PsiStatement>(targetClass, outsideRefClass))
      myFixture.doHighlighting()
      assertTrue(hasReportedProblems<PsiAssignmentExpression>(targetClass, packageRefClass))

      changeField(targetClass) { field -> field.modifierList?.setModifierProperty(PsiModifier.PUBLIC, true) }
      // too many usages now, cannot analyse all of them, so we give up and remove old errors
      assertFalse(hasReportedProblems<PsiStatement>(targetClass, outsideRefClass))
      assertFalse(hasReportedProblems<PsiAssignmentExpression>(targetClass, packageRefClass))
    }
  }

  fun testReportOnlyOneMemberProblemsOnEachLine() {
    val targetClass = myFixture.addClass("""
      package foo;
      public class A {
        static String field1, field2;
      }
    """.trimIndent())

    myFixture.addClass("""
      package bar;
      import foo.A;
      public class B {
        void test() {
          A.field1 = "bar";
          A.field2 = "baz";
        }
      }
    """.trimIndent())

    doTest(targetClass) {
      val fields = targetClass.fields
      val field = fields[0]

      WriteCommandAction.runWriteCommandAction(project) {
        val modifiers = field.modifierList!!
        modifiers.setModifierProperty(PsiModifier.PUBLIC, true)
        modifiers.setModifierProperty(PsiModifier.STATIC, false)
      }
      myFixture.doHighlighting()
      val inlays = ProjectProblemPassUtils.getInlays(myFixture.editor)
      UsefulTestCase.assertSize(1, inlays.entries)
    }
  }

  fun testMakeFinalFieldPublic() {
    val targetClass = myFixture.addClass("""
      package foo;
      public class A {
        static final String field = "foo";
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package bar;
      import foo.A;
      public class B {
        void test() {
          A.field = "bar";
        }
      }
    """.trimIndent())

    doTest(targetClass) {
      changeField(targetClass) {
        val modifiers = it.modifierList!!
        modifiers.setModifierProperty(PsiModifier.PUBLIC, true)
      }
      assertTrue(hasReportedProblems<PsiAssignmentExpression>(targetClass, refClass))

      changeField(targetClass) {
        val modifiers = it.modifierList!!
        modifiers.setModifierProperty(PsiModifier.FINAL, false)
      }
      assertEmpty(getProblems(targetClass.containingFile))
    }
  }

  fun testRenameEnumConstant() {
    val targetClass = myFixture.addClass("""
      package foo;
      public enum MyEnum {
        FOO
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package bar;
      import foo.*;
      public class B {
        void test() {
          MyEnum myEnum = MyEnum.FOO;
        }
      }
    """.trimIndent())

    doTest(targetClass) {
      changeField(targetClass) { psiField ->
        val identifier = psiField.nameIdentifier
        val factory = JavaPsiFacade.getInstance(project).elementFactory
        identifier.replace(factory.createIdentifier("BAR"))
      }
      assertTrue(hasReportedProblems<PsiDeclarationStatement>(targetClass, refClass))
    }
  }

  private fun doFieldTest(fieldChangeAction: (PsiField, PsiElementFactory) -> Unit) {
    val targetClass = myFixture.addClass("""
      package foo;
      public class A {
        public static String field = null;
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package bar;
      import foo.A;
      public class B {
        void test() {
          A.field = "foo";
        }
      }
    """.trimIndent())

    doTest(targetClass) {
      changeField(targetClass) {
        val factory = JavaPsiFacade.getInstance(project).elementFactory
        fieldChangeAction(it, factory)
      }
      assertTrue(hasReportedProblems<PsiAssignmentExpression>(targetClass, refClass))
    }
  }

  private fun changeField(targetClass: PsiClass, changeAction: (PsiField) -> Unit) {
    val fields = targetClass.fields
    assertSize(1, fields)
    val field = fields[0]

    WriteCommandAction.runWriteCommandAction(project) { changeAction(field) }
    myFixture.doHighlighting()
  }
}