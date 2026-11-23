// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.codeInsight.daemon.problems.Problem
import com.intellij.codeInsight.daemon.problems.pass.ProjectProblemUtils
import com.intellij.java.syntax.parser.JavaKeywords
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiKeyword
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringFactory
import com.intellij.refactoring.move.moveInner.MoveInnerImpl
import com.intellij.refactoring.openapi.impl.MoveInnerRefactoringImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class ClassProblemsTest {
  companion object {
    private val projectFixture = projectProblemsProjectFixture()
  }

  private val problems by projectProblemsFixture(projectFixture)
  private val myFixture get() = problems.fixture
  @Test
  fun testRename(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doClassTest { psiClass, factory ->
        psiClass.nameIdentifier?.replace(factory.createIdentifier("Bar"))
      }
    }
  }

  @Test
  fun testMakeClassFinal(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doClassTest { psiClass, _ ->
        psiClass.modifierList?.setModifierProperty(PsiModifier.FINAL, true)
      }
    }
  }

  @Test
  fun testMakeClassPackagePrivate(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doClassTest { psiClass, _ ->
        psiClass.modifierList?.setModifierProperty(PsiModifier.PUBLIC, false)
      }
    }
  }

  @Test
  fun testChangeClassHierarchy(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doClassTest { psiClass, factory ->
        psiClass.extendsList?.replace(factory.createReferenceList(PsiJavaCodeReferenceElement.EMPTY_ARRAY))
      }
    }
  }

  @Test
  fun testSealedClassPermittedInheritors(): Unit = timeoutRunBlocking {
    val targetClass = myFixture.addClass("""
        package foo;
        public sealed class A {
        }
      """.trimIndent())
    val refClass = myFixture.addClass("""
        package foo;
        
        public class B extends A {
        }
      """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeClass(targetClass) { psiClass, _ ->
          val factory = PsiFileFactory.getInstance(myFixture.project)
          val javaFile = factory.createFileFromText(JavaLanguage.INSTANCE, "class __Dummy permits B {}") as PsiJavaFile
          val dummyClass = javaFile.classes[0]
          val permitsList = dummyClass.permitsList
          psiClass.addAfter(permitsList!!, psiClass.implementsList)
        }
        assertTrue(problems.hasReportedProblems<PsiClass>(refClass))
        myFixture.openFileInEditor(refClass.containingFile.virtualFile)
        changeClass(refClass) { psiClass, _ ->
          psiClass.modifierList?.setModifierProperty(PsiModifier.FINAL, true)
        }
        myFixture.openFileInEditor(targetClass.containingFile.virtualFile)
        myFixture.doHighlighting()
        assertFalse(problems.hasReportedProblems<PsiClass>(refClass))
      }
    }
  }
  
  @Test
  fun testClassOverrideBecamePrivate(): Unit = timeoutRunBlocking {
    myFixture.addClass("""
        package foo;
        
        public abstract class Parent {
          abstract void test();
        }
    """.trimIndent())
    val targetClass = myFixture.addClass("""
        package foo;
        
        public class Foo extends Parent {
          void test() {}
        }
    """.trimIndent())
    val refClass = myFixture.addClass("""
        package foo;
        
        public class Usage {
          void use() {
            Foo foo = new Foo();
            foo.test();
          }
        }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeClass(targetClass) { psiClass, factory -> 
          psiClass.methods[0].parameterList.add(factory.createParameterFromText("int a", psiClass))
        }

        changeClass(targetClass) { psiClass, _ ->
          psiClass.methods[0].modifierList.setModifierProperty(PsiModifier.PUBLIC, true)
        }
        
        changeClass(targetClass) { psiClass, factory ->
          psiClass.extendsList?.replace(factory.createReferenceList(PsiJavaCodeReferenceElement.EMPTY_ARRAY))
        }
      }
      
      assertTrue(problems.hasReportedProblems<PsiMethod>(refClass))
    }
  }

  @Test
  fun testMakeClassInterface(): Unit = timeoutRunBlocking {
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
    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeClass(targetClass) { psiClass, factory ->
          val classKeyword = PsiTreeUtil.getPrevSiblingOfType(psiClass.nameIdentifier, PsiKeyword::class.java)
          val interfaceKeyword = factory.createKeyword(JavaKeywords.INTERFACE)
          classKeyword?.replace(interfaceKeyword)
        }
        assertTrue(problems.hasReportedProblems<PsiDeclarationStatement>(refClass))
      }
    }
  }

  @Test
  fun testMakeNestedInner(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doNestedClassTest(true)
    }
  }

  @Test
  fun testMakeInnerNested(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      doNestedClassTest(false)
    }
  }

  @Test
  fun testMakeClassAnnotationType(): Unit = timeoutRunBlocking {
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

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeClass(targetClass) { _, factory ->
          val annoType = factory.createAnnotationType("A")
          targetClass = targetClass.replace(annoType) as PsiClass
        }
      }
      assertTrue(problems.hasReportedProblems<PsiDeclarationStatement>(refClass))
    }
  }

  @Test
  fun testInheritedMethodUsage(): Unit = timeoutRunBlocking {
    myFixture.addClass("""
        package foo;
        
        public class Parent {
          public void foo() {}
        }
      """.trimIndent())

    val aClass = myFixture.addClass("""
        package foo;
        
        public class A extends Parent {
        }
      """.trimIndent())

    val refClass = myFixture.addClass("""
        package foo;
        
        public class Usage {
          void test() {
            (new A()).foo();
          }
        }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(aClass) {
        changeClass(aClass) { psiClass, factory ->
          psiClass.extendsList?.replace(factory.createReferenceList(PsiJavaCodeReferenceElement.EMPTY_ARRAY))
        }

        assertTrue(problems.hasReportedProblems<PsiClass>(refClass))
      }
    }
  }

  @Test
  fun testMoveInnerClassAndUndo(): Unit = timeoutRunBlocking {
    val refClass = myFixture.addClass("""
        public class A {
          private String s = "foo";
          public class Inner {
            void test() {
              System.out.println(s);
            }
          }
        }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(refClass.containingFile.virtualFile)

      problems.doTest(refClass) {
        changeClass(refClass) { psiClass, _ ->
          val innerClass = psiClass.innerClasses[0]
          val targetContainer = MoveInnerImpl.getTargetContainer(innerClass, false)!!
          val moveRefactoring = MoveInnerRefactoringImpl(myFixture.project, innerClass, innerClass.name, true, "a", targetContainer)
          BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts<Throwable> { moveRefactoring.run() }
        }
        changeClass(refClass) { psiClass, _ ->
          psiClass.fields[0].modifierList?.setModifierProperty(PsiModifier.PRIVATE, false)
        }
        changeClass(refClass) { psiClass, _ ->
          psiClass.fields[0].modifierList?.setModifierProperty(PsiModifier.PRIVATE, true)
        }

        assertThat(problems.getProblems()).isNotEmpty()

        val selectedEditor = FileEditorManager.getInstance(myFixture.project).selectedEditor
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          UndoManager.getInstance(myFixture.project).undo(selectedEditor)
          UndoManager.getInstance(myFixture.project).undo(selectedEditor)
          UndoManager.getInstance(myFixture.project).undo(selectedEditor)
        }
        PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()

        myFixture.doHighlighting()
        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).isEmpty()
      }
    }
  }

  @Test
  fun testRenameClassRenameMethodAndUndoAll(): Unit = timeoutRunBlocking {
    val targetClass = myFixture.addClass("""
        public class A {
          public void foo() {}
          
          public void bar() {}
        }
    """.trimIndent())

    myFixture.addClass("""
        public class RefClass {
          void test() {
            A a = new A();
            a.foo();
            a.bar();
          }
        }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        val factory = JavaPsiFacade.getInstance(myFixture.project).elementFactory

        changeClass(targetClass) { psiClass, _ ->
          psiClass.identifyingElement?.replace(factory.createIdentifier("A1"))
        }

        val problems: Map<PsiMember, Set<Problem>> = ProjectProblemUtils.getReportedProblems(myFixture.editor)
        val reportedMembers = problems.map { it.key }
        assertThat(reportedMembers).hasSize(1)
        assertTrue(targetClass in reportedMembers)

        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          val method = targetClass.findMethodsByName("foo", false)[0]
          method.identifyingElement?.replace(factory.createIdentifier("foo1"))
        }
        myFixture.doHighlighting()
        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).isNotEmpty()

        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          val method = targetClass.findMethodsByName("foo1", false)[0]
          method.identifyingElement?.replace(factory.createIdentifier("foo"))
        }
        myFixture.doHighlighting()
        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).hasSize(2)

        changeClass(targetClass) { psiClass, _ ->
          psiClass.identifyingElement?.replace(factory.createIdentifier("A"))
        }

        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).isEmpty()
      }
    }
  }

  @Test
  fun testAddMissingTypeParam(): Unit = timeoutRunBlocking {
    val targetClass = myFixture.addClass("""
      class TargetClass {
      }
    """.trimIndent())

    myFixture.addClass("""
      public class RefClass extends TargetClass<T> {
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeClass(targetClass) { psiClass, _ ->
          psiClass.modifierList?.setModifierProperty(PsiModifier.PUBLIC, true)
        }

        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).hasSize(1)

        changeClass(targetClass) { psiClass, factory ->
          val typeParameterList = factory.createTypeParameterList()
          typeParameterList.add(factory.createTypeParameterFromText("T", psiClass))
          psiClass.typeParameterList?.replace(typeParameterList)
        }

        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).isEmpty()
      }
    }
  }

  @Test
  fun testRenameClassAndFixUsages(): Unit = timeoutRunBlocking {
    val targetClass = myFixture.addClass("""
      class AClass {
      }
    """.trimIndent())

    myFixture.addClass("""
      class BClass {
        AClass tmp;

        public void method1(String arg) {
            System.out.println(tmp.toString());
        }
      }
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      problems.doTest(targetClass) {
        changeClass(targetClass) { psiClass, factory ->
          psiClass.nameIdentifier?.replace(factory.createIdentifier("AClass1"))
        }

        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).hasSize(1)

        changeClass(targetClass) { psiClass, _ ->
          psiClass.setName("AClass")
          val renameRefactoring = RefactoringFactory.getInstance(myFixture.project).createRename(psiClass, "AClass1", true, false)
          renameRefactoring.run()
        }

        assertThat(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries).isEmpty()
      }
    }
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

    problems.doTest(targetClass) {
      changeClass(targetClass) { psiClass, _ ->
        val innerClass = psiClass.findInnerClassByName("Inner", false)
        innerClass?.modifierList?.setModifierProperty(PsiModifier.STATIC, !isStatic)
      }
      assertTrue(problems.hasReportedProblems<PsiDeclarationStatement>(refClass))
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

    problems.doTest(targetClass) {
      changeClass(targetClass, classChangeAction)
      assertTrue(problems.hasReportedProblems<PsiDeclarationStatement>(refClass))
    }
  }

  private fun changeClass(targetClass: PsiClass, classChangeAction: (PsiClass, PsiElementFactory) -> Unit) {
    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      val factory = JavaPsiFacade.getInstance(myFixture.project).elementFactory
      classChangeAction(targetClass, factory)
    }
    myFixture.doHighlighting()
  }
}
