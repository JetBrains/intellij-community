// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.codeInsight.daemon.problems.pass.ProjectProblemUtils
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiStatement
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class FieldProblemsTest {
  companion object {
    private val projectFixture = projectProblemsProjectFixture()
  }

  private val problems by projectProblemsFixture(projectFixture)
  private val myFixture get() = problems.fixture

  @Test
  fun testRenameField(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doFieldTest { field: PsiField, factory: PsiElementFactory ->
        field.nameIdentifier.replace(factory.createIdentifier("f"))
      }
    }
  }

  @Test
  fun testChangeFieldType(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doFieldTest { field: PsiField, factory: PsiElementFactory ->
        field.typeElement?.replace(factory.createTypeElementFromText(CommonClassNames.JAVA_LANG_INTEGER, null))
      }
    }
  }

  @Test
  fun testMakeFieldPackagePrivate(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doFieldTest { psiField, _ ->
        psiField.modifierList?.setModifierProperty(PsiModifier.PUBLIC, false)
      }
    }
  }

  @Test
  fun testMakeFieldPrivate(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doFieldTest { psiField, _ ->
        psiField.modifierList?.setModifierProperty(PsiModifier.PUBLIC, false)
        psiField.modifierList?.setModifierProperty(PsiModifier.PRIVATE, true)
      }
    }
  }

  @Test
  fun testMakeFieldNonStatic(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doFieldTest { psiField, _ ->
        psiField.modifierList?.setModifierProperty(PsiModifier.STATIC, false)
      }
    }
  }

  @Test
  fun testMakeFieldFinal(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doFieldTest { psiField, _ ->
        psiField.modifierList?.setModifierProperty(PsiModifier.FINAL, true)
      }
    }
  }

  @Test
  fun testRenameFieldAndUndo(): Unit = timeoutRunBlocking {
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

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {

        val factory = JavaPsiFacade.getInstance(myFixture.project).elementFactory

        changeField(targetClass) {
          val replacement = factory.createFieldFromText("public static String f;", targetClass)
          it.replace(replacement)
        }

        assertTrue(problems.hasReportedProblems<PsiLocalVariable>(refClass))

        changeField(targetClass) {
          val replacement = factory.createFieldFromText("public static String field;", targetClass)
          it.replace(replacement)
        }

        assertThat(problems.getProblems()).isEmpty()
      }
    }
  }

  @Test
  fun testTwoClassesWithSameFieldName(): Unit = timeoutRunBlocking {
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

    withContext(Dispatchers.EDT) {
      problems.doTest(bClass) {
        val factory = JavaPsiFacade.getInstance(myFixture.project).elementFactory
        val field = factory.createFieldFromText("public static String mySpecialField;", bClass)
        WriteCommandAction.runWriteCommandAction(myFixture.project) { bClass.add(field) }
        myFixture.doHighlighting()
        assertThat(problems.getProblems()).isEmpty()

        myFixture.openFileInEditor(aClass.containingFile.virtualFile)
        myFixture.doHighlighting()
        changeField(aClass) { it.modifierList?.setModifierProperty(PsiModifier.STATIC, false) }
        assertTrue(problems.hasReportedProblems<PsiLocalVariable>(refClass))
      }
    }
  }

  @Test
  fun testAmbiguousReference(): Unit = timeoutRunBlocking {
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

    withContext(Dispatchers.EDT) {
      problems.doTest(bClass) {
        val factory = JavaPsiFacade.getInstance(myFixture.project).elementFactory
        val field = factory.createFieldFromText("public static String field;", bClass)
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          bClass.add(field)
        }
        myFixture.doHighlighting()
        assertTrue(problems.hasReportedProblems<PsiLocalVariable>(cClass))
      }
    }
  }

  @Test
  fun testTooManyUsages(): Unit = timeoutRunBlocking {
    val targetClass = myFixture.addClass("""
      package foo;
      public class A {
        public static String field;
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
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


      Registry.get("ide.unused.symbol.calculation.maxFilesToSearchUsagesIn").setValue(nRefClasses + 1)

      problems.doTest(targetClass) {
        val factory = JavaPsiFacade.getInstance(myFixture.project).elementFactory

        changeField(targetClass) { it.nameIdentifier.replace(factory.createIdentifier("f")) }
        assertTrue(problems.hasReportedProblems<PsiLocalVariable>(*refClasses.toTypedArray()))

        changeField(targetClass) { it.nameIdentifier.replace(factory.createIdentifier("field")) }
        val psiFile = targetClass.containingFile
        assertThat(problems.getProblems()).isEmpty()

        myFixture.addClass(refClassText.replace("classname", "RefClass$nRefClasses"))

        changeField(targetClass) { it.nameIdentifier.replace(factory.createIdentifier("f")) }
        assertThat(problems.getProblems()).isEmpty()
      }
    }
  }

  @Test
  fun testErrorsRemovedAfterScopeChanged(): Unit = timeoutRunBlocking {
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

    withContext(Dispatchers.EDT) {
      Registry.get("ide.unused.symbol.calculation.maxFilesToSearchUsagesIn").setValue(2)

      problems.doTest(targetClass) {

        val outsideRefClass = myFixture.addClass("""
        package bar;

        import foo.A;

        public class RefClass1 {
          void test() {
            System.out.println(A.field);
          }
        }
      """.trimIndent())

        val factory = JavaPsiFacade.getElementFactory(myFixture.project)
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          val psiField = factory.createFieldFromText("static final String field = \"foo\";", null)
          targetClass.add(psiField)
        }
        myFixture.doHighlighting()

        assertFalse(problems.hasReportedProblems<PsiStatement>(outsideRefClass))
        myFixture.doHighlighting()
        assertTrue(problems.hasReportedProblems<PsiAssignmentExpression>(packageRefClass))

        changeField(targetClass) { field -> field.modifierList?.setModifierProperty(PsiModifier.PUBLIC, true) }
        // too many usages now, cannot analyse all of them, so we give up and remove old errors
        assertFalse(problems.hasReportedProblems<PsiStatement>(outsideRefClass))
        assertFalse(problems.hasReportedProblems<PsiAssignmentExpression>(packageRefClass))
      }
    }
  }

  @Test
  fun testReportOnlyOneMemberProblemsOnEachLine(): Unit = timeoutRunBlocking {
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

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        val fields = targetClass.fields
        val field = fields[0]

        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          val modifiers = field.modifierList!!
          modifiers.setModifierProperty(PsiModifier.PUBLIC, true)
          modifiers.setModifierProperty(PsiModifier.STATIC, false)
        }
        myFixture.doHighlighting()
        val inlays = ProjectProblemUtils.getReportedProblems(
          myFixture.editor)
        assertThat(inlays.entries).hasSize(1)
      }
    }
  }

  @Test
  fun testMakeFinalFieldPublic(): Unit = timeoutRunBlocking {
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

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeField(targetClass) {
          val modifiers = it.modifierList!!
          modifiers.setModifierProperty(PsiModifier.PUBLIC, true)
        }
        assertTrue(problems.hasReportedProblems<PsiAssignmentExpression>(refClass))

        changeField(targetClass) {
          val modifiers = it.modifierList!!
          modifiers.setModifierProperty(PsiModifier.FINAL, false)
        }
        assertThat(problems.getProblems()).isEmpty()
      }
    }
  }

  @Test
  fun testRenameEnumConstant(): Unit = timeoutRunBlocking {
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

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeField(targetClass) { psiField ->
          val identifier = psiField.nameIdentifier
          val factory = JavaPsiFacade.getInstance(myFixture.project).elementFactory
          identifier.replace(factory.createIdentifier("BAR"))
        }
        assertTrue(problems.hasReportedProblems<PsiDeclarationStatement>(refClass))
      }
    }
  }

  @Test
  fun testFieldWithTypeParameter(): Unit = timeoutRunBlocking {
    myFixture.addClass("""
      public class Id<T> {
        T id(T t) {
          return t;
        }
      }
    """.trimIndent())

    val targetClass = myFixture.addClass("""
      public class TargetClass {
        final static Id<Integer> ID = new ID(); 
      }
    """.trimIndent())

    myFixture.addClass("""
      public class RefClass {
        void test() {
          TargetClass.ID.id("foo");
        }
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeField(targetClass) { psiField ->
          psiField.modifierList?.setModifierProperty(PsiModifier.PUBLIC, true)
        }

        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).hasSize(1)

        changeField(targetClass) { psiField ->
          val factory = JavaPsiFacade.getInstance(myFixture.project).elementFactory
          val typeElement = factory.createTypeElementFromText("Id<String>", targetClass)
          psiField.typeElement?.replace(typeElement)
        }

        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).isEmpty()
      }
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

    problems.doTest(targetClass) {
      changeField(targetClass) {
        val factory = JavaPsiFacade.getInstance(myFixture.project).elementFactory
        fieldChangeAction(it, factory)
      }
      assertTrue(problems.hasReportedProblems<PsiAssignmentExpression>(refClass))
    }
  }

  private fun changeField(targetClass: PsiClass, changeAction: (PsiField) -> Unit) {
    val fields = targetClass.fields
    assertThat(fields).hasSize(1)
    val field = fields[0]

    WriteCommandAction.runWriteCommandAction(myFixture.project) { changeAction(field) }
    myFixture.doHighlighting()
  }
}