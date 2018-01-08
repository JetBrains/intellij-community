/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.psi

import com.intellij.codeInsight.AnnotationTargetUtil
import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassImpl
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.psi.util.PsiUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

import java.util.concurrent.Callable

class JavaStubsTest extends LightCodeInsightFixtureTestCase {

  void "test resolve from annotation method default"() {
    def cls = myFixture.addClass("""
      public @interface BrokenAnnotation {
        enum Foo {DEFAULT, OTHER}
        Foo value() default Foo.DEFAULT;
      }
      """.stripIndent())

    def file = cls.containingFile as PsiFileImpl
    assert file.stub

    def ref = (cls.methods[0] as PsiAnnotationMethod).defaultValue
    assert file.stub

    assert ref instanceof PsiReferenceExpression
    assert ref.resolve() == cls.innerClasses[0].fields[0]
    assert file.stub
  }

  void "test literal annotation value"() {
    def cls = myFixture.addClass("""
      class Foo {
        @org.jetbrains.annotations.Contract(pure=true)
        native int foo();
      }
      """.stripIndent())

    def file = cls.containingFile as PsiFileImpl
    assert ControlFlowAnalyzer.isPure(cls.methods[0])
    assert file.stub
    assert !file.contentsLoaded
  }

  void "test local variable annotation doesn't cause stub-ast switch"() {
    def cls = myFixture.addClass("""
      class Foo {
        @Anno int foo() {
          @Anno int var = 2;
        }
      }
      @interface Anno {}
      """)

    def file = cls.containingFile as PsiFileImpl
    assert AnnotatedElementsSearch.searchPsiMethods(myFixture.findClass("Anno"), GlobalSearchScope.allScope(project)).size() == 1
    assert file.stub
    assert !file.contentsLoaded
  }

  void "test applying type annotations"() {
    def cls = myFixture.addClass("""
      import java.lang.annotation.*;
      class Foo {
        @Target(ElementType.TYPE_USE)
        @interface TA { String value(); }

        private @TA String f1;

        private static @TA int m1(@TA int p1) { return 0; }
      }
      """.stripIndent())

    def f1 = cls.fields[0].type
    def m1 = cls.methods[0].returnType
    def p1 = cls.methods[0].parameterList.parameters[0].type
    assert (cls as PsiClassImpl).stub

    assert f1.getCanonicalText(true) == "java.lang.@Foo.TA String"
    assert m1.getCanonicalText(true) == "@Foo.TA int"
    assert p1.getCanonicalText(true) == "@Foo.TA int"
  }

  void "test containing class of a local class is null"() {
    def foo = myFixture.addClass("class Foo {{ class Bar extends Foo {} }}")
    def bar = ClassInheritorsSearch.search(foo).findFirst()

    def file = (PsiFileImpl)foo.containingFile
    assert !file.contentsLoaded

    assert bar.containingClass == null
    assert !file.contentsLoaded

    bar.node
    assert bar.containingClass == null
    assert file.contentsLoaded
  }

  void "test stub-based super class type parameter resolve"() {
    for (int i = 0; i < 100; i++) {
      def foo = myFixture.addClass("class Foo$i<T> {}")
      def bar = myFixture.addClass("class Bar$i<T> extends Foo$i<T> {}")

      def app = ApplicationManager.application
      app.executeOnPooledThread({ ReadAction.compute { bar.node } })
      def superType = app.executeOnPooledThread({ ReadAction.compute { bar.superTypes[0] }} as Callable<PsiClassType>).get()
      assert foo == superType.resolve()
      assert bar.typeParameters[0] == PsiUtil.resolveClassInClassTypeOnly(superType.parameters[0])
    }
  }

  void "test default annotation attribute name"() {
    def cls = myFixture.addClass('@Anno("foo") class Foo {}')
    def file = (PsiFileImpl)cls.containingFile
    assert !file.contentsLoaded

    def attr = cls.modifierList.annotations[0].parameterList.attributes[0]
    assert attr.name == null
    assert !file.contentsLoaded

    attr.node
    assert attr.name == null
  }

  void "test determine annotation target without AST"() {
    def cls = myFixture.addClass('''
import java.lang.annotation.*;
@Anno class Some {} 
@Target(ElementType.METHOD) @interface Anno {}''')
    assert 'Some' == cls.name
    assert !AnnotationTargetUtil.isTypeAnnotation(cls.modifierList.annotations[0])
    assert !((PsiFileImpl) cls.containingFile).contentsLoaded
  }

  void "test parameter list count"() {
    def list = myFixture.addClass('class Cls { void foo(a) {} }').methods[0].parameterList
    assert list.parametersCount == list.parameters.size()
  }

  void "test deprecated enum constant"() {
    def cls = myFixture.addClass("enum Foo { c1, @Deprecated c2, /** @deprecated */ c3 }")
    assert !((PsiFileImpl) cls.containingFile).contentsLoaded

    assert !cls.fields[0].deprecated
    assert cls.fields[1].deprecated
    assert cls.fields[2].deprecated

    assert !((PsiFileImpl) cls.containingFile).contentsLoaded
  }

  void "test breaking and adding import does not cause stub AST mismatch"() {
    def file = myFixture.addFileToProject("a.java", "import foo.*; import bar.*; class Foo {}") as PsiJavaFile
    def another = myFixture.addClass("package zoo; public class Another {}")
    WriteCommandAction.runWriteCommandAction(project) { 
      file.viewProvider.document.insertString(file.text.indexOf('import'), 'x')
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      file.importClass(another)
    }
    PsiTestUtil.checkStubsMatchText(file)
  }

  void "test removing import in broken code does not cause stub AST mismatch"() {
    def file = myFixture.addFileToProject("a.java", "import foo..module.SomeClass; class Foo {}") as PsiJavaFile
    WriteCommandAction.runWriteCommandAction(project) { 
      file.importList.importStatements[0].delete()
    }
    PsiTestUtil.checkStubsMatchText(file)
  }
  
  void "test adding type before method call does not cause stub AST mismatch"() {
    def file = myFixture.addFileToProject("a.java", """
class Foo {
  void foo() {
    something();
    call();
  }
}
""") as PsiJavaFile
    WriteCommandAction.runWriteCommandAction(project) { 
      file.viewProvider.document.insertString(file.text.indexOf('call'), 'char ')
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      PsiTestUtil.checkStubsMatchText(file)
    }
  }

  void "test inserting class keyword"() {
    String text = "class Foo { void foo() { return; } }"
    PsiFile psiFile = myFixture.addFileToProject("a.java", text)
    Document document = psiFile.getViewProvider().getDocument()

    WriteCommandAction.runWriteCommandAction(project) { 
      document.insertString(text.indexOf("return"), "class ") 
    }
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
    PsiTestUtil.checkStubsMatchText(psiFile)
  }

  void "test inserting enum keyword"() {
    String text = "class Foo { void foo() { return; } }"
    PsiFile psiFile = myFixture.addFileToProject("a.java", text)
    Document document = psiFile.getViewProvider().getDocument()

    WriteCommandAction.runWriteCommandAction(project) {
      document.insertString(text.indexOf("return"), "enum Foo")
    }
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
    PsiTestUtil.checkStubsMatchText(psiFile)
  }

  void "test type arguments without type in a method"() {
    String text = "class Foo { { final Collection<String> contexts; f instanceof -> } }"
    PsiFile psiFile = myFixture.addFileToProject("a.java", text)

    WriteCommandAction.runWriteCommandAction(project) { deleteString(psiFile, "Collection") }
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
    PsiTestUtil.checkStubsMatchText(psiFile)
  }

  private static void deleteString(PsiFile file, String fragment) {
    def document = file.viewProvider.document
    def index = document.text.indexOf(fragment)
    document.deleteString(index, index + fragment.size())
  }

  void "test remove class literal qualifier"() {
    String text = "class Foo { { foo(String.class); } }"
    PsiFile psiFile = myFixture.addFileToProject("a.java", text)
    WriteCommandAction.runWriteCommandAction(project) {
      psiFile.viewProvider.document.insertString(text.indexOf(');'), ' x')
      WriteCommandAction.runWriteCommandAction(project) { deleteString(psiFile, "String") }
    }
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
    PsiTestUtil.checkStubsMatchText(psiFile)
  }

  void "test annotation stub without reference"() {
    PsiFile psiFile = myFixture.addFileToProject("a.java", "@() class Foo { } }")
    assert ((PsiJavaFile) psiFile).classes[0].modifierList.annotations[0].nameReferenceElement == null
    assert !((PsiFileImpl) psiFile).contentsLoaded
  }

  void "test anonymous class generics"() {
    PsiFileImpl file = (PsiFileImpl) myFixture.addFileToProject("A.java", """
class A {
    <V> Object foo() {
      return new I<V>(){};
    }
}

interface I<T> {}
""")

    PsiClass a = ((PsiJavaFile) file).classes[0]
    PsiClass i = ((PsiJavaFile) file).classes[1]
    PsiAnonymousClass anon = assertOneElement(DirectClassInheritorsSearch.search(i).findAll()) as PsiAnonymousClass

    assert i == anon.baseClassType.resolve()
    assert a.methods[0].typeParameters[0] == PsiUtil.resolveClassInClassTypeOnly(anon.baseClassType.parameters[0])

    assert !file.contentsLoaded
  }

  void "test broken anonymous"() {
    String text = """
class A {
  public GroupDescriptor[] getGroupDescriptors() {
    return new ThreadGroup(Descriptor[]{
      new GroupDescriptor(groupId, "test")
    };
  }
}"""
    PsiFile psiFile = myFixture.addFileToProject("a.java", text)
    WriteCommandAction.runWriteCommandAction(project) {
      psiFile.viewProvider.document.insertString(text.indexOf(']{'), 'x')
    }
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
    PsiTestUtil.checkStubsMatchText(psiFile)
  }

  void "test broken nested anonymous"() {
    PsiTestUtil.checkStubsMatchText(myFixture.addFileToProject("a.java", "class A { { new A(new B[a]{b}); } }"))
  }

  void "test lone angle brackets"() {
    String text = """
class A {
  {
    PsiLanguageInjectionHost host = PsiTreeUtil.getParentOfType(element, .class);
    final <PsiElement, TextRange> pair;
  }  
}"""
    PsiTestUtil.checkStubsMatchText(myFixture.addFileToProject("a.java", text))
  }

}