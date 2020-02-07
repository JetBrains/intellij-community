// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.*
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination

internal class FieldProblemsTest : ProjectProblemsViewTest() {

  fun testRenameField() = doFieldTest { field: PsiField, factory: PsiElementFactory ->
    field.nameIdentifier.replace(factory.createIdentifier("f"))
  }

  fun testChangeFieldType() = doFieldTest { field: PsiField, factory: PsiElementFactory ->
    field.typeElement?.replace(factory.createTypeElementFromText(CommonClassNames.JAVA_LANG_INTEGER, null))
  }

  fun testChangeFieldAccessModifier() = doFieldTest { psiField, _ ->
    psiField.modifierList?.setModifierProperty(PsiModifier.PUBLIC, false)
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

    doTest(targetClass) { problemsCollector ->

      val factory = JavaPsiFacade.getInstance(project).elementFactory

      changeField(targetClass) {
        val replacement = factory.createFieldFromText("public static String f;", targetClass)
        it.replace(replacement)
      }

      assertTrue(hasReportedProblems<PsiLocalVariable>(refClass, problemsCollector))

      changeField(targetClass) {
        val replacement = factory.createFieldFromText("public static String field;", targetClass)
        it.replace(replacement)
      }

      assertEmpty(problemsCollector.getProblems(refClass.containingFile.virtualFile))
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

    doTest(bClass) { problemsCollector ->
      val factory = JavaPsiFacade.getInstance(project).elementFactory
      val field = factory.createFieldFromText("public static String field;", bClass)
      WriteCommandAction.runWriteCommandAction(project) {
        bClass.add(field)
      }
      myFixture.checkHighlighting()
      assertTrue(hasReportedProblems<PsiLocalVariable>(cClass, problemsCollector))
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

    Registry.get("ide.unused.symbol.calculation.maxFilesToSearchUsagesIn").setValue(nRefClasses + 1)

    doTest(targetClass) { problemsCollector ->
      val factory = JavaPsiFacade.getInstance(project).elementFactory

      changeField(targetClass) { it.nameIdentifier.replace(factory.createIdentifier("f")) }
      assertTrue(refClasses.all { hasReportedProblems<PsiLocalVariable>(it, problemsCollector) })

      changeField(targetClass) { it.nameIdentifier.replace(factory.createIdentifier("field")) }
      assertEmpty(problemsCollector.problems)

      myFixture.addClass(refClassText.replace("classname", "RefClass$nRefClasses"))

      changeField(targetClass) { it.nameIdentifier.replace(factory.createIdentifier("f")) }
      assertEmpty(problemsCollector.problems)
    }
  }

  fun testErrorsRemovedAfterScopeChanged() {
    val targetClass = myFixture.addClass("""
      package foo;
      public class A {
        static final String field = "foo";
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

    Registry.get("ide.unused.symbol.calculation.maxFilesToSearchUsagesIn").setValue(2)

    doTest(targetClass) { problemsCollector ->

      val outsideRefClass = myFixture.addClass("""
      package bar;

      import foo.A;

      public class RefClass1 {
        void test() {
          System.out.println(A.field);
        }
      }
    """.trimIndent())
      assertFalse(hasReportedProblems<PsiStatement>(outsideRefClass, problemsCollector))
      assertTrue(hasReportedProblems<PsiAssignmentExpression>(packageRefClass, problemsCollector))

      changeField(targetClass) {field -> field.modifierList!!.setModifierProperty(PsiModifier.PUBLIC, true) }
      // too many usages now, cannot analyse all of them, so we give up and remove old errors
      assertFalse(hasReportedProblems<PsiStatement>(outsideRefClass, problemsCollector))
      assertFalse(hasReportedProblems<PsiAssignmentExpression>(packageRefClass, problemsCollector))
    }
  }

  fun testReportProblemWhenTargetFileRemoved() {
    val targetClass = myFixture.addClass("""
      package foo;
      public class A {
        public static String field;
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package bar;
      import foo.A;
      class B {
        void test() {
          String aField = A.field;
        }
      }
    """.trimIndent())

    val dummyClass = myFixture.addClass("class Dummy {}")

    doTest(dummyClass) { problemsCollector ->
      val file = targetClass.containingFile.virtualFile
      WriteCommandAction.runWriteCommandAction(project) { file.delete(this) }
      myFixture.doHighlighting()
      assertTrue(hasReportedProblems<PsiLocalVariable>(refClass, problemsCollector))
    }
  }

  fun testReportProblemWhenTargetFileMoved() {
    val targetClass = myFixture.addClass("""
      package foo;
      public class A {
        public static String field;
      }
    """.trimIndent())

    val fooRefClass = myFixture.addClass("""
      package foo;
      class FooRef {
        void test() {
          String aField = A.field;
        }
      }
    """.trimIndent())

    val barRefClass = myFixture.addClass("""
      package bar;
      import foo.A;
      class BarRef {
        void test() {
          String aField = A.field;
        }
      }
    """.trimIndent())

    doTest(targetClass) { problemsCollector ->
      changeField(targetClass) {
        val modifiers = it.modifierList!!
        modifiers.setModifierProperty(PsiModifier.PUBLIC, false)
      }
      assertTrue(hasReportedProblems<PsiLocalVariable>(barRefClass, problemsCollector))

      val barRefClassOwner = barRefClass.containingFile as PsiClassOwner
      val destDir = MoveClassesOrPackagesUtil.chooseDestinationPackage(project, barRefClassOwner.packageName, null)!!
      barRefClass.containingFile.virtualFile

      val packageWrapper = PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(destDir))
      val moveDestination = SingleSourceRootMoveDestination(packageWrapper, destDir)
      MoveClassesOrPackagesProcessor(project, arrayOf(targetClass), moveDestination, true, true, null).run()
      myFixture.checkHighlighting()

      assertEmpty(problemsCollector.getProblems(barRefClass.containingFile.virtualFile))
      assertTrue(hasReportedProblems<PsiLocalVariable>(fooRefClass, problemsCollector))
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

    doTest(targetClass) { problemsCollector ->
      val refFile = refClass.containingFile.virtualFile
      changeField(targetClass) {
        val modifiers = it.modifierList!!
        modifiers.setModifierProperty(PsiModifier.PUBLIC, true)
      }
      assertTrue(hasReportedProblems<PsiAssignmentExpression>(refClass, problemsCollector))

      changeField(targetClass) {
        val modifiers = it.modifierList!!
        modifiers.setModifierProperty(PsiModifier.FINAL, false)
      }
      assertEmpty(problemsCollector.getProblems(refFile))
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

    doTest(targetClass) { problemsCollector ->
      changeField(targetClass) { psiField ->
        val identifier = psiField.nameIdentifier
        val factory = JavaPsiFacade.getInstance(project).elementFactory
        identifier.replace(factory.createIdentifier("BAR"))
      }
      assertTrue(hasReportedProblems<PsiDeclarationStatement>(refClass, problemsCollector))
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

    doTest(targetClass) { problemsCollector ->
      changeField(targetClass) {
        val factory = JavaPsiFacade.getInstance(project).elementFactory
        fieldChangeAction(it, factory)
      }
      assertTrue(hasReportedProblems<PsiAssignmentExpression>(refClass, problemsCollector))
    }
  }

  private fun changeField(targetClass: PsiClass, changeAction: (PsiField) -> Unit) {
    val fields = targetClass.fields
    assertSize(1, fields)
    val field = fields[0]

    WriteCommandAction.runWriteCommandAction(project) { changeAction(field) }
    myFixture.checkHighlighting()
  }
}