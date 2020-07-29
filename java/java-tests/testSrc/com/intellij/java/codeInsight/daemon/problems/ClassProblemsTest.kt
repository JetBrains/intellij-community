// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.codeInsight.daemon.problems.pass.ProjectProblemPassUtils
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.moveInner.MoveInnerImpl
import com.intellij.refactoring.openapi.impl.MoveInnerRefactoringImpl

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

  fun testInheritedMethodUsage() {
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

    doTest(aClass) {
      changeClass(aClass) { psiClass, factory ->
        psiClass.extendsList?.replace(factory.createReferenceList(PsiJavaCodeReferenceElement.EMPTY_ARRAY))
      }

      assertTrue(hasReportedProblems<PsiClass>(aClass, refClass))
    }
  }

  fun testMoveInnerClassAndUndo() {
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

    myFixture.openFileInEditor(refClass.containingFile.virtualFile)

    doTest(refClass) {
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

      assertNotEmpty(ProjectProblemPassUtils.getInlays(myFixture.editor).entries)

      val selectedEditor = FileEditorManager.getInstance(project).selectedEditor
      WriteCommandAction.runWriteCommandAction(project) {
        UndoManager.getInstance(project).undo(selectedEditor)
        UndoManager.getInstance(project).undo(selectedEditor)
        UndoManager.getInstance(project).undo(selectedEditor)
      }
      PsiDocumentManager.getInstance(project).commitAllDocuments()

      assertEmpty(ProjectProblemPassUtils.getInlays(myFixture.editor).entries)
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
