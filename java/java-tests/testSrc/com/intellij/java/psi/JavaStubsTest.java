// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi

import com.intellij.codeInsight.AnnotationTargetUtil
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassImpl
import com.intellij.psi.impl.source.PsiFieldImpl
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import groovy.transform.CompileStatic

import java.util.concurrent.Callable

@CompileStatic
class JavaStubsTest extends LightJavaCodeInsightFixtureTestCase {

  void "test resolve from annotation method default"() {
    def cls = myFixture.addClass("""\
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
    def cls = myFixture.addClass("""\
      class Foo {
        @org.jetbrains.annotations.Contract(pure=true)
        native int foo();
      }
      """.stripIndent())

    def file = cls.containingFile as PsiFileImpl
    assert JavaMethodContractUtil.isPure(cls.methods[0])
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
    def cls = myFixture.addClass("""\
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
    def foo = myFixture.addClass("""\
      class Foo {
        static { class Bar extends Foo { } }
      }""".stripIndent())
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
    def cls = myFixture.addClass("""\
      import java.lang.annotation.*;
      @Anno class Some {} 
      @Target(ElementType.METHOD) @interface Anno {}""".stripIndent())
    assert "Some" == cls.name
    assert !AnnotationTargetUtil.isTypeAnnotation(cls.modifierList.annotations[0])
    assert !((PsiFileImpl) cls.containingFile).contentsLoaded
  }

  void "test parameter list count"() {
    myFixture.addFileToProject("a.java", "class Cls { void foo(a) {} }")
    def list = myFixture.findClass("Cls").methods[0].parameterList
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
      file.viewProvider.document.insertString(file.text.indexOf("import"), "x")
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
    def file = myFixture.addFileToProject("a.java", """\
      class Foo {
        void foo() {
          something();
          call();
        }
      }""".stripIndent()) as PsiJavaFile
    WriteCommandAction.runWriteCommandAction(project) {
      file.viewProvider.document.insertString(file.text.indexOf("call"), "char ")
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
      psiFile.viewProvider.document.insertString(text.indexOf(");"), " x")
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

  void "test anonymous class stubs see method type parameters"() {
    PsiFileImpl file = (PsiFileImpl) myFixture.addFileToProject("A.java", """\
      class A {
          <V> Object foo() {
            return new I<V>(){};
          }
      }
      interface I<T> {}
      """.stripIndent())

    PsiClass a = ((PsiJavaFile) file).classes[0]
    PsiClass i = ((PsiJavaFile) file).classes[1]
    PsiAnonymousClass anon = assertOneElement(DirectClassInheritorsSearch.search(i).findAll()) as PsiAnonymousClass

    assert i == anon.baseClassType.resolve()
    assert a.methods[0].typeParameters[0] == PsiUtil.resolveClassInClassTypeOnly(anon.baseClassType.parameters[0])

    assert !file.contentsLoaded
  }

  void "test anonymous class stubs see local classes"() {
    PsiFileImpl file = (PsiFileImpl) myFixture.addFileToProject("A.java", """\
      class A {
          void foo() {
            class Local {}
            new I<Local>(){};
          }
      }
      interface I<T> {}
      """.stripIndent())

    PsiClass i = ((PsiJavaFile) file).classes[1]
    PsiAnonymousClass anon = assertOneElement(DirectClassInheritorsSearch.search(i).findAll()) as PsiAnonymousClass

    assert !file.contentsLoaded

    assert i == anon.baseClassType.resolve()
    assert PsiUtil.resolveClassInClassTypeOnly(anon.baseClassType.parameters[0])?.name == "Local"
  }

  void "test local class stubs see other local classes"() {
    PsiFileImpl file = (PsiFileImpl) myFixture.addFileToProject("A.java", """\
      class A {
          void foo() {
            class Local1 {}
            class Local2 extends Local1 {}
          }
      }
      """.stripIndent())

    def cache = PsiShortNamesCache.getInstance(project)
    def local1 = cache.getClassesByName("Local1", GlobalSearchScope.allScope(project))[0]
    def local2 = cache.getClassesByName("Local2", GlobalSearchScope.allScope(project))[0]

    assert !file.contentsLoaded

    assert local1 == local2.superClass
  }

  void "test local class stubs do not load AST for inheritance checking when possible"() {
    PsiFileImpl file = (PsiFileImpl) myFixture.addFileToProject("A.java", """\
      class A {
          void foo() {
            class UnrelatedLocal {}
            class Local extends A {}
          }
      }
      """.stripIndent())

    def local = PsiShortNamesCache.getInstance(project).getClassesByName("Local", GlobalSearchScope.allScope(project))[0]
    assert "A" == local.superClass.name
    assert !file.contentsLoaded
  }

  void "test broken anonymous"() {
    String text = """\
      class A {
        public GroupDescriptor[] getGroupDescriptors() {
          return new ThreadGroup(Descriptor[]{
            new GroupDescriptor(groupId, "test")
          };
        }
      }""".stripIndent()
    PsiFile psiFile = myFixture.addFileToProject("a.java", text)
    WriteCommandAction.runWriteCommandAction(project) {
      psiFile.viewProvider.document.insertString(text.indexOf("]{"), "x")
    }
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
    PsiTestUtil.checkStubsMatchText(psiFile)
  }

  void "test broken nested anonymous"() {
    PsiTestUtil.checkStubsMatchText(myFixture.addFileToProject("a.java", "class A { { new A(new B[a]{b}); } }"))
  }

  void "test lone angle brackets"() {
    String text = """\
      class A {
        {
          PsiLanguageInjectionHost host = PsiTreeUtil.getParentOfType(element, .class);
          final <PsiElement, TextRange> pair;
        }  
      }""".stripIndent()
    PsiTestUtil.checkStubsMatchText(myFixture.addFileToProject("a.java", text))
  }

  void "test incomplete static import does not cause CCE"() {
    def file = myFixture.addFileToProject("a.java", "import static foo.bar.") as PsiJavaFile
    assert ((PsiFileImpl)file).stub
    assert file.node
    def staticImport = ((PsiJavaFile)file).importList.importStaticStatements[0]
    assert staticImport.referenceName == null
    assert !staticImport.resolveTargetClass()
  }

  void "test adding import to broken file with type parameters"() {
    def file = myFixture.addFileToProject("a.java", "A<B>") as PsiJavaFile
    WriteCommandAction.runWriteCommandAction(project) {
      file.importClass(myFixture.findClass(CommonClassNames.JAVA_UTIL_LIST))
    }
    PsiTestUtil.checkStubsMatchText(file)
  }

  void "test remove extends reference before dot"() {
    def file = myFixture.addFileToProject("a.java", "class A extends B. { int a; }")
    WriteCommandAction.runWriteCommandAction(project) {
      def javaFile = file as PsiJavaFile
      def clazz = (javaFile.classes[0] as PsiImplicitClass).innerClasses[0]
      clazz.extendsList.referenceElements[0].delete()
    }
    PsiTestUtil.checkStubsMatchText(file)
  }

  void "test remove type argument list after space"() {
    def file = myFixture.addFileToProject("a.java", "class A { A <B>a; }")
    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.findClass("A").fields[0].typeElement.innermostComponentReferenceElement.parameterList.delete()
    }
    PsiTestUtil.checkStubsMatchText(file)
    PsiTestUtil.checkFileStructure(file)
  }

  void "test remove modifier making a comment a class javadoc"() {
    def file = myFixture.addFileToProject("a.java", "import foo; final /** @deprecated */ public class A { }")
    WriteCommandAction.runWriteCommandAction(project) {
      def clazz = myFixture.findClass("A")
      def children = clazz.modifierList.children
      assert children.length != 0 : clazz.containingFile.text + ";" + clazz.containingFile.virtualFile.path
      children[0].delete()
    }
    PsiTestUtil.checkFileStructure(file)
    PsiTestUtil.checkStubsMatchText(file)
  }

  void "test add reference into broken extends list"() {
    def file = myFixture.addFileToProject("a.java", "class A extends.ends Foo { int a; }")
    WriteCommandAction.runWriteCommandAction(project) {
      def javaFile = file as PsiJavaFile
      def clazz = (javaFile.classes[0] as PsiImplicitClass).innerClasses[0]
      clazz.extendsList.add(JavaPsiFacade.getElementFactory(project).createReferenceElementByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, file.resolveScope))
    }
    PsiTestUtil.checkStubsMatchText(file)
  }

  void "test identifier dot before class"() {
    def file = myFixture.addFileToProject("a.java", "class A {{ public id.class B {}}}")
    PsiTestUtil.checkStubsMatchText(file)
  }

  void "test removing orphan annotation"() {
    String text = """\
      public class Foo {
          public Foo() {
          }
      
          @Override
        public void initSteps {
        }
      }""".stripIndent()
    PsiFile psiFile = myFixture.addFileToProject("a.java", text)
    WriteCommandAction.runWriteCommandAction(project) {
      PsiTreeUtil.findChildOfType(psiFile, PsiAnnotation).delete()
    }
    PsiTestUtil.checkStubsMatchText(psiFile)
  }

  void "test local record"() {
    PsiTestUtil.checkStubsMatchText(myFixture.addFileToProject("a.java", """\
      class A {
        void test() {
          record A(String s) { }
        }
      }
      """.stripIndent()))
  }

  void "test field with missing initializer"() {
    def file = myFixture.addFileToProject("a.java", "class A { int a = ; } ")
    def clazz = myFixture.findClass("A")
    assert PsiFieldImpl.getDetachedInitializer(clazz.fields[0]) == null
    assert !(file as PsiFileImpl).contentsLoaded
  }

  void "test implicit class"() {

    def psiFile = myFixture.addFileToProject("a.java", """\
      void test() {
      }
      
      String s = "foo";
      int i = 10;
      static {} // not allowed by spec, but we parse
      """.stripIndent())
    PsiTestUtil.checkStubsMatchText(psiFile)
  }

  void "test array type use annotation stubbing"() {
    myFixture.addClass("""\
      import java.lang.annotation.*;
      @Target(ElementType.TYPE_USE)
      @interface Anno { int value(); }""".stripIndent())

    PsiClass clazz = myFixture.addClass("""\
      class Foo {
        <T> @Anno(0) String @Anno(1) [] @Anno(2) [] foo(@Anno(3) byte @Anno(4) [] data) {}
        List<String> @Anno(5) [] field;
      }""".stripIndent())

    def method = clazz.methods[0]
    def field = clazz.fields[0]
    def parameter = method.parameterList.parameters[0]
    def parameterAnnotations = parameter.type.annotations
    def parameterComponentAnnotations = (parameter.type as PsiArrayType).componentType.annotations
    def methodAnnotations = method.returnType.annotations
    def methodComponentAnnotations = (method.returnType as PsiArrayType).componentType.annotations
    def methodDeepComponentAnnotations = method.returnType.deepComponentType.annotations
    def fieldAnnotations = field.type.annotations

    assert !(clazz.containingFile as PsiFileImpl).contentsLoaded
    assertAnnotationValueText methodDeepComponentAnnotations, "0"
    assertAnnotationValueText methodAnnotations, "1"
    assertAnnotationValueText methodComponentAnnotations, "2"
    assertAnnotationValueText parameterComponentAnnotations, "3"
    assertAnnotationValueText parameterAnnotations, "4"
    assertAnnotationValueText fieldAnnotations, "5"
    assert !(clazz.containingFile as PsiFileImpl).contentsLoaded

    assert clazz.node // load AST
    assert parameter.type.annotations.size() == 1
  }

  private static void assertAnnotationValueText(PsiAnnotation[] annotations, String text) {
    assert annotations.size() == 1
    assert annotations[0].findAttributeValue("value").text == text
  }
}
