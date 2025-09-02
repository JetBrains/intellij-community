// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.codeInsight.daemon.problems.Problem
import com.intellij.codeInsight.daemon.problems.pass.ProjectProblemUtils
import com.intellij.java.syntax.parser.JavaKeywords
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringFactory
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

  fun testSealedClassPermittedInheritors() {
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

    doTest(targetClass) {
      changeClass(targetClass) { psiClass, _ ->
        val factory = PsiFileFactory.getInstance(project)
        val javaFile = factory.createFileFromText(JavaLanguage.INSTANCE, "class __Dummy permits B {}") as PsiJavaFile
        val dummyClass = javaFile.classes[0]
        val permitsList = dummyClass.permitsList
        psiClass.addAfter(permitsList!!, psiClass.implementsList)
      }
      assertTrue(hasReportedProblems<PsiClass>(refClass))
      myFixture.openFileInEditor(refClass.containingFile.virtualFile)
      changeClass(refClass) { psiClass, _ ->
        psiClass.modifierList?.setModifierProperty(PsiModifier.FINAL, true)
      }
      myFixture.openFileInEditor(targetClass.containingFile.virtualFile)
      myFixture.doHighlighting()
      assertFalse(hasReportedProblems<PsiClass>(refClass))
    }
  }
  
  fun testClassOverrideBecamePrivate() {
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
    
    doTest(targetClass) {
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
    
    assertTrue(hasReportedProblems<PsiMethod>(refClass))
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
        val interfaceKeyword = factory.createKeyword(JavaKeywords.INTERFACE)
        classKeyword?.replace(interfaceKeyword)
      }
      assertTrue(hasReportedProblems<PsiDeclarationStatement>(refClass))
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
    assertTrue(hasReportedProblems<PsiDeclarationStatement>(refClass))
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

      assertTrue(hasReportedProblems<PsiClass>(refClass))
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

      assertNotEmpty(getProblems())

      val selectedEditor = FileEditorManager.getInstance(project).selectedEditor
      WriteCommandAction.runWriteCommandAction(project) {
        UndoManager.getInstance(project).undo(selectedEditor)
        UndoManager.getInstance(project).undo(selectedEditor)
        UndoManager.getInstance(project).undo(selectedEditor)
      }
      PsiDocumentManager.getInstance(project).commitAllDocuments()

      myFixture.doHighlighting()
      assertEmpty(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries)
    }
  }

  fun testRenameClassRenameMethodAndUndoAll() {
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

    doTest(targetClass) {
      val factory = JavaPsiFacade.getInstance(project).elementFactory

      changeClass(targetClass) { psiClass, _ ->
        psiClass.identifyingElement?.replace(factory.createIdentifier("A1"))
      }

      val problems: Map<PsiMember, Set<Problem>> = ProjectProblemUtils.getReportedProblems(myFixture.editor)
      val reportedMembers = problems.map { it.key }
      assertSize(1, reportedMembers)
      assertTrue(targetClass in reportedMembers)

      WriteCommandAction.runWriteCommandAction(project) {
        val method = targetClass.findMethodsByName("foo", false)[0]
        method.identifyingElement?.replace(factory.createIdentifier("foo1"))
      }
      myFixture.doHighlighting()
      assertNotEmpty(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries)

      WriteCommandAction.runWriteCommandAction(project) {
        val method = targetClass.findMethodsByName("foo1", false)[0]
        method.identifyingElement?.replace(factory.createIdentifier("foo"))
      }
      myFixture.doHighlighting()
      assertSize(2, ProjectProblemUtils.getReportedProblems(myFixture.editor).entries)

      changeClass(targetClass) { psiClass, _ ->
        psiClass.identifyingElement?.replace(factory.createIdentifier("A"))
      }

      assertEmpty(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries)
    }
  }

  fun testAddMissingTypeParam() {
    val targetClass = myFixture.addClass("""
      class TargetClass {
      }
    """.trimIndent())

    myFixture.addClass("""
      public class RefClass extends TargetClass<T> {
      }
    """.trimIndent())

    doTest(targetClass) {
      changeClass(targetClass) { psiClass, _ ->
        psiClass.modifierList?.setModifierProperty(PsiModifier.PUBLIC, true)
      }

      assertSize(1, ProjectProblemUtils.getReportedProblems(myFixture.editor).entries)

      changeClass(targetClass) { psiClass, factory ->
        val typeParameterList = factory.createTypeParameterList()
        typeParameterList.add(factory.createTypeParameterFromText("T", psiClass))
        psiClass.typeParameterList?.replace(typeParameterList)
      }

      assertEmpty(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries)
    }
  }

  fun testRenameClassAndFixUsages() {
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

    doTest(targetClass) {
      changeClass(targetClass) { psiClass, factory ->
        psiClass.nameIdentifier?.replace(factory.createIdentifier("AClass1"))
      }

      assertSize(1, ProjectProblemUtils.getReportedProblems(myFixture.editor).entries)

      changeClass(targetClass) { psiClass, _ ->
        psiClass.setName("AClass")
        val renameRefactoring = RefactoringFactory.getInstance(project).createRename(psiClass, "AClass1", true, false)
        renameRefactoring.run()
      }

      assertEmpty(ProjectProblemUtils.getReportedProblems(myFixture.editor).entries)
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
      assertTrue(hasReportedProblems<PsiDeclarationStatement>(refClass))
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
      assertTrue(hasReportedProblems<PsiDeclarationStatement>(refClass))
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
