// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.idea.TestFor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.java.stubs.index.JavaImplicitClassIndex;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaStubsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void test_resolve_from_annotation_method_default() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass cls = myFixture.addClass("""
                                         public @interface BrokenAnnotation {
                                           enum Foo {DEFAULT, OTHER}
                                           Foo value() default Foo.DEFAULT;
                                         }""");

    ReadAction.runBlocking(() -> {
      PsiFileImpl file = (PsiFileImpl)cls.getContainingFile();
      assertNotNull(file.getStub());

      PsiAnnotationMemberValue ref = (((PsiAnnotationMethod)cls.getMethods()[0])).getDefaultValue();
      assertNotNull(file.getStub());

      assertTrue(ref instanceof PsiReferenceExpression);
      assertEquals(((PsiReferenceExpression)ref).resolve(), cls.getInnerClasses()[0].getFields()[0]);
      assertNotNull(file.getStub());
    });
  }

  @Test
  public void test_literal_annotation_value() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass cls = myFixture.addClass("""
                                         class Foo {
                                           @org.jetbrains.annotations.Contract(pure=true)
                                           native int foo();
                                         }""");

    ReadAction.runBlocking(() -> {
      PsiFileImpl file = (PsiFileImpl)cls.getContainingFile();
      assertTrue(JavaMethodContractUtil.isPure(cls.getMethods()[0]));
      assertNotNull(file.getStub());
      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  public void test_local_variable_annotation_doesn_t_cause_stub_ast_switch() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass cls = myFixture.addClass("""
      class Foo {
        @Anno int foo() {
          @Anno int var = 2;
        }
      }
      @interface Anno {}""");

    ReadAction.runBlocking(() -> {
      PsiFileImpl file = (PsiFileImpl)cls.getContainingFile();
      assertEquals(1, AnnotatedElementsSearch.searchPsiMethods(myFixture.findClass("Anno"), GlobalSearchScope.allScope(myFixture.getProject()))
        .findAll().size());
      assertNotNull(file.getStub());
      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  public void test_applying_type_annotations() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass cls = myFixture.addClass("""
       import java.lang.annotation.*;
       class Foo {
         @Target(ElementType.TYPE_USE)
         @interface TA { String value(); }

         private @TA String f1;

         private static @TA int m1(@TA int p1) { return 0; }
       }""");

    ReadAction.runBlocking(() -> {
      PsiType f1 = cls.getFields()[0].getType();
      PsiType m1 = cls.getMethods()[0].getReturnType();
      PsiType p1 = cls.getMethods()[0].getParameterList().getParameters()[0].getType();
      assertNotNull(((PsiClassImpl)cls).getStub());

      assertEquals("java.lang.@Foo.TA String", f1.getCanonicalText(true));
      assertEquals("@Foo.TA int", m1.getCanonicalText(true));
      assertEquals("@Foo.TA int", p1.getCanonicalText(true));
    });
  }

  @Test
  public void test_containing_class_of_a_local_class_is_null() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass foo = myFixture.addClass("""
                                        class Foo {
                                          static { class Bar extends Foo { } }
                                        }""");
    ReadAction.runBlocking(() -> {
      PsiClass bar = ClassInheritorsSearch.search(foo).findFirst();

      PsiFileImpl file = (PsiFileImpl)foo.getContainingFile();
      assertFalse(file.isContentsLoaded());

      assertNull(bar.getContainingClass());
      assertFalse(file.isContentsLoaded());

      assertNotNull(bar.getNode());
      assertNull(bar.getContainingClass());
      assertTrue(file.isContentsLoaded());
    });
  }

  @Test
  public void test_stub_based_super_class_type_parameter_resolve() throws ExecutionException, InterruptedException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    for (int i = 0; i < 100; i++) {
      PsiClass foo = myFixture.addClass("class Foo" + i + "<T> {}");
      final PsiClass bar = myFixture.addClass("class Bar" + i + "<T> extends Foo" + i + "<T> {}");

      Application app = ApplicationManager.getApplication();
      app.executeOnPooledThread(() -> ReadAction.compute(() -> bar.getNode()));
      PsiClassType superType = app.executeOnPooledThread(() -> ReadAction.compute(() -> bar.getSuperTypes()[0])).get();
      ReadAction.runBlocking(() -> assertEquals(foo, superType.resolve()));
      ReadAction.runBlocking(() -> assertEquals(bar.getTypeParameters()[0], PsiUtil.resolveClassInClassTypeOnly(superType.getParameters()[0])));
    }
  }

  @Test
  public void test_default_annotation_attribute_name() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass cls = myFixture.addClass("@Anno(\"foo\") class Foo {}");
    ReadAction.runBlocking(() -> {
      PsiFileImpl file = (PsiFileImpl)cls.getContainingFile();
      assertFalse(file.isContentsLoaded());

      PsiNameValuePair attr = cls.getModifierList().getAnnotations()[0].getParameterList().getAttributes()[0];
      assertNull(attr.getName());
      assertFalse(file.isContentsLoaded());

      assertNotNull(attr.getNode());
      assertNull(attr.getName());
    });
  }

  @Test
  public void test_determine_annotation_target_without_AST() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass cls = myFixture.addClass("""
                                        import java.lang.annotation.*;
                                        @Anno class Some {}
                                        @Target(ElementType.METHOD) @interface Anno {}""");
    ReadAction.runBlocking(() -> {
      assertEquals("Some", cls.getName());
      assertFalse(AnnotationTargetUtil.isTypeAnnotation(cls.getModifierList().getAnnotations()[0]));
      assertFalse(((PsiFileImpl)cls.getContainingFile()).isContentsLoaded());
    });
  }

  @Test
  public void test_parameter_list_count() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("a.java", "class Cls { void foo(a) {} }");
    ReadAction.runBlocking(() -> {
      PsiParameterList list = myFixture.findClass("Cls").getMethods()[0].getParameterList();
      assertEquals(list.getParametersCount(), list.getParameters().length);
    });
  }

  @Test
  public void test_deprecated_enum_constant() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass cls = myFixture.addClass("""
      enum Foo {
        c1, @Deprecated c2, /** @deprecated */ c3,
        /// @deprecated no real deprecation
        c4
      }""");
    ReadAction.runBlocking(() -> {
      assertFalse(((PsiFileImpl)cls.getContainingFile()).isContentsLoaded());

      assertFalse(cls.getFields()[0].isDeprecated());
      assertTrue(cls.getFields()[1].isDeprecated());
      assertTrue(cls.getFields()[2].isDeprecated());
      assertFalse(cls.getFields()[3].isDeprecated());

      assertFalse(((PsiFileImpl)cls.getContainingFile()).isContentsLoaded());
    });
  }

  @Test
  public void test_malformed_generic_in_implements_list_does_not_cause_stub_AST_mismatch() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("A.java", """
      private static class A implements BiConsumer<List<A>, List<A>n>> {}
      """);
    ReadAction.runBlocking(() -> {
      PsiReferenceList implementsList = file.getClasses()[0].getImplementsList();
      // getReferenceElements() always reads from AST; getReferencedTypes() reads via the stub when available.
      assertEquals(implementsList.getReferenceElements().length, implementsList.getReferencedTypes().length);
    });
  }

  @Test
  public void test_breaking_and_adding_import_does_not_cause_stub_AST_mismatch() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("a.java", "import foo.*; import bar.*; class Foo {}");
    PsiClass another = myFixture.addClass("package zoo; public class Another {}");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), (Computable<Boolean>)() -> {
      file.getViewProvider().getDocument().insertString(file.getText().indexOf("import"), "x");
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      return file.importClass(another);
    });
    ReadAction.runBlocking(() -> {
      PsiTestUtil.checkStubsMatchText(file);
    });
  }

  @Test
  public void test_removing_import_in_broken_code_does_not_cause_stub_AST_mismatch() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("a.java", "import foo..module.SomeClass; class Foo {}");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> file.getImportList().getImportStatements()[0].delete());
    ReadAction.runBlocking(() -> {
      PsiTestUtil.checkStubsMatchText(file);
    });
  }

  @Test
  public void test_adding_type_before_method_call_does_not_cause_stub_AST_mismatch() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("a.java", """
      class Foo {
        void foo() {
          something();
          call();
        }
      }""");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      file.getViewProvider().getDocument().insertString(file.getText().indexOf("call"), "char ");
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      PsiTestUtil.checkStubsMatchText(file);
    });
  }

  @Test
  public void test_inserting_class_keyword() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String text = "class Foo { void foo() { return; } }";
    PsiFile psiFile = myFixture.addFileToProject("a.java", text);
    final Document document = ReadAction.computeBlocking(() -> psiFile.getViewProvider().getDocument());

    WriteCommandAction.runWriteCommandAction(
      myFixture.getProject(), () -> document.insertString(text.indexOf("return"), "class "));
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments());
    ReadAction.runBlocking(() -> PsiTestUtil.checkStubsMatchText(psiFile));
  }

  @Test
  public void test_inserting_enum_keyword() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String text = "class Foo { void foo() { return; } }";
    PsiFile psiFile = myFixture.addFileToProject("a.java", text);
    final Document document = ReadAction.computeBlocking(() -> psiFile.getViewProvider().getDocument());

    WriteCommandAction.runWriteCommandAction(
      myFixture.getProject(), () -> document.insertString(text.indexOf("return"), "enum Foo"));
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments());
    ReadAction.runBlocking(() -> PsiTestUtil.checkStubsMatchText(psiFile));
  }

  @Test
  public void test_type_arguments_without_type_in_a_method() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String text = "class Foo { { final Collection<String> contexts; f instanceof -> } }";
    final PsiFile psiFile = myFixture.addFileToProject("a.java", text);

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> deleteString(psiFile, "Collection"));
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments());
    ReadAction.runBlocking(() -> PsiTestUtil.checkStubsMatchText(psiFile));
  }

  private static void deleteString(PsiFile file, String fragment) {
    Document document = file.getViewProvider().getDocument();
    int index = document.getText().indexOf(fragment);
    document.deleteString(index, index + fragment.length());
  }

  @Test
  public void test_remove_class_literal_qualifier() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String text = "class Foo { { foo(String.class); } }";
    final PsiFile psiFile = myFixture.addFileToProject("a.java", text);
    WriteCommandAction.runWriteCommandAction(
      myFixture.getProject(), () -> {
        psiFile.getViewProvider().getDocument().insertString(text.indexOf(");"), " x");
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> deleteString(psiFile, "String"));
      });
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments());
    ReadAction.runBlocking(() -> PsiTestUtil.checkStubsMatchText(psiFile));
  }

  @Test
  public void test_annotation_stub_without_reference() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile psiFile = myFixture.addFileToProject("a.java", "@() class Foo { } }");
    ReadAction.runBlocking(() -> {
      assertNull(((PsiJavaFile)psiFile).getClasses()[0].getModifierList().getAnnotations()[0].getNameReferenceElement());
      assertFalse(((PsiFileImpl)psiFile).isContentsLoaded());
    });
  }

  @Test
  public void test_anonymous_class_stubs_see_method_type_parameters() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("A.java", """
      class A {
          <V> Object foo() {
            return new I<V>(){};
          }
      }
      interface I<T> {}""");

    ReadAction.runBlocking(() -> {
      PsiClass a = ((PsiJavaFile)file).getClasses()[0];
      PsiClass i = ((PsiJavaFile)file).getClasses()[1];
      PsiAnonymousClass anon =
        (PsiAnonymousClass)assertThat(DirectClassInheritorsSearch.search(i).findAll()).singleElement().actual();

      assertEquals(i, anon.getBaseClassType().resolve());
      assertEquals(a.getMethods()[0].getTypeParameters()[0], PsiUtil.resolveClassInClassTypeOnly(anon.getBaseClassType().getParameters()[0]));

      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  public void test_anonymous_class_stubs_see_local_classes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("A.java", """
      class A {
          void foo() {
            class Local {}
            new I<Local>(){};
          }
      }
      interface I<T> {}""");

    ReadAction.runBlocking(() -> {
      PsiClass i = ((PsiJavaFile)file).getClasses()[1];
      PsiAnonymousClass anon =
        (PsiAnonymousClass)assertThat(DirectClassInheritorsSearch.search(i).findAll()).singleElement().actual();

      assertFalse(file.isContentsLoaded());

      assertEquals(i, anon.getBaseClassType().resolve());
      final PsiClass only = PsiUtil.resolveClassInClassTypeOnly(anon.getBaseClassType().getParameters()[0]);
      assertEquals("Local", (only == null ? null : only.getName()));
    });
  }

  @Test
  public void test_local_class_stubs_see_other_local_classes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("A.java", """
      class A {
          void foo() {
            class Local1 {}
            class Local2 extends Local1 {}
          }
      }""");

    ReadAction.runBlocking(() -> {
      PsiShortNamesCache cache = PsiShortNamesCache.getInstance(myFixture.getProject());
      PsiClass local1 = cache.getClassesByName("Local1", GlobalSearchScope.allScope(myFixture.getProject()))[0];
      PsiClass local2 = cache.getClassesByName("Local2", GlobalSearchScope.allScope(myFixture.getProject()))[0];

      assertFalse(file.isContentsLoaded());

      assertEquals(local1, local2.getSuperClass());
    });
  }

  @Test
  public void test_local_class_stubs_do_not_load_AST_for_inheritance_checking_when_possible() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("A.java", """
       class A {
           void foo() {
             class UnrelatedLocal {}
             class Local extends A {}
           }
       }""");

    ReadAction.runBlocking(() -> {
      PsiClass local = PsiShortNamesCache.getInstance(myFixture.getProject()).getClassesByName("Local", GlobalSearchScope.allScope(myFixture.getProject()))[0];
      assertEquals("A", local.getSuperClass().getName());
      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  public void test_broken_anonymous() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String text = """
      class A {
        public GroupDescriptor[] getGroupDescriptors() {
          return new ThreadGroup(Descriptor[]{
            new GroupDescriptor(groupId, "test")
          };
        }
      }""";
    final PsiFile psiFile = myFixture.addFileToProject("a.java", text);
    WriteCommandAction.runWriteCommandAction(
      myFixture.getProject(), () -> psiFile.getViewProvider().getDocument().insertString(text.indexOf("]{"), "x"));
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments());
    ReadAction.runBlocking(() -> PsiTestUtil.checkStubsMatchText(psiFile));
  }

  @Test
  public void test_broken_nested_anonymous() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("a.java", "class A { { new A(new B[a]{b}); } }");
    ReadAction.runBlocking(() -> PsiTestUtil.checkStubsMatchText(file));
  }

  @Test
  public void test_lone_angle_brackets() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String text = """
      class A {
        {
          PsiLanguageInjectionHost host = PsiTreeUtil.getParentOfType(element, .class);
          final <PsiElement, TextRange> pair;
        }
      }""";
    PsiFile file = myFixture.addFileToProject("a.java", text);
    ReadAction.runBlocking(() -> PsiTestUtil.checkStubsMatchText(file));
  }

  @Test
  public void test_incomplete_static_import_does_not_cause_CCE() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("a.java", "import static foo.bar.");
    ReadAction.runBlocking(() -> {
      assertNotNull(((PsiFileImpl)file).getStub());
      assertNotNull(file.getNode());
      PsiImportStaticStatement staticImport = file.getImportList().getImportStaticStatements()[0];
      assertNull(staticImport.getReferenceName());
      assertNull(staticImport.resolveTargetClass());
    });
  }

  @Test
  public void test_adding_import_to_broken_file_with_type_parameters() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("a.java", "A<B>");
    WriteCommandAction.runWriteCommandAction(
      myFixture.getProject(), (Computable<Boolean>)() -> file.importClass(myFixture.findClass(CommonClassNames.JAVA_UTIL_LIST)));
    ReadAction.runBlocking(() -> {
      PsiTestUtil.checkStubsMatchText(file);
    });
  }

  @Test
  public void test_remove_extends_reference_before_dot() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiFile file = myFixture.addFileToProject("a.java", "class A extends B. { int a; }");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      PsiJavaFile javaFile = (PsiJavaFile)file;
      PsiClass clazz = javaFile.getClasses()[0].getInnerClasses()[0];
      clazz.getExtendsList().getReferenceElements()[0].delete();
    });
    ReadAction.runBlocking(() -> {
      PsiTestUtil.checkStubsMatchText(file);
    });
  }

  @Test
  public void test_remove_type_argument_list_after_space() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("a.java", "class A { A <B>a; }");
    WriteCommandAction.runWriteCommandAction(
      myFixture.getProject(),
      () -> myFixture.findClass("A").getFields()[0].getTypeElement().getInnermostComponentReferenceElement().getParameterList().delete());
    ReadAction.runBlocking(() -> {
      PsiTestUtil.checkStubsMatchText(file);
      PsiTestUtil.checkFileStructure(file);
    });
  }

  @Test
  public void test_remove_modifier_making_a_comment_a_class_javadoc() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("a.java", "import foo; final /** @deprecated */ public class A { }");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      PsiClass clazz = myFixture.findClass("A");
      PsiElement[] children = clazz.getModifierList().getChildren();
      assertTrue(children.length != 0,
                 clazz.getContainingFile().getText() + ";" + clazz.getContainingFile().getVirtualFile().getPath());
      children[0].delete();
    });
    ReadAction.runBlocking(() -> {
      PsiTestUtil.checkFileStructure(file);
      PsiTestUtil.checkStubsMatchText(file);
    });
  }

  @Test
  public void test_add_reference_into_broken_extends_list() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiFile file = myFixture.addFileToProject("a.java", "class A extends.ends Foo { int a; }");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), (Computable<PsiElement>)() -> {
      PsiJavaFile javaFile = (PsiJavaFile)file;
      PsiClass clazz = javaFile.getClasses()[0].getInnerClasses()[0];
      return clazz.getExtendsList().add(JavaPsiFacade.getElementFactory(myFixture.getProject())
                                          .createReferenceElementByFQClassName(CommonClassNames.JAVA_LANG_OBJECT,
                                                                               file.getResolveScope()));
    });
    ReadAction.runBlocking(() -> {
      PsiTestUtil.checkStubsMatchText(file);
    });
  }

  @Test
  public void test_identifier_dot_before_class() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("a.java", "class A {{ public id.class B {}}}");
    ReadAction.runBlocking(() -> {
      PsiTestUtil.checkStubsMatchText(file);
    });
  }

  @Test
  public void test_removing_orphan_annotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String text = """
       public class Foo {
         public Foo() {
         }

         @Override
         public void initSteps {
         }
       }""";
    final PsiFile psiFile = myFixture.addFileToProject("a.java", text);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> PsiTreeUtil.findChildOfType(psiFile, PsiAnnotation.class).delete());
    ReadAction.runBlocking(() -> {
      PsiTestUtil.checkStubsMatchText(psiFile);
    });
  }

  @Test
  public void test_local_record() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("a.java", """
       class A {
         void test() {
           record A(String s) { }
         }
       }""");
    ReadAction.runBlocking(() -> PsiTestUtil.checkStubsMatchText(file));
  }

  @Test
  public void test_field_with_missing_initializer() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("a.java", "class A { int a = ; } ");
    ReadAction.runBlocking(() -> {
      PsiClass clazz = myFixture.findClass("A");
      assertNull(PsiFieldImpl.getDetachedInitializer(clazz.getFields()[0]));
      assertFalse(((PsiFileImpl)file).isContentsLoaded());
    });
  }

  @Test
  public void test_implicit_class() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile psiFile = myFixture.addFileToProject("a.java", """
      void test() {
      }

      String s = "foo";
      int i = 10;
      static {} // not allowed by spec, but we parse""");
    ReadAction.runBlocking(() -> {
      PsiTestUtil.checkStubsMatchText(psiFile);
    });
  }

  @Test
  public void test_implicit_class_index_updated_after_file_rename() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile psiFile = myFixture.addFileToProject("Hello.java", "void main() {}");
    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());

    ReadAction.runBlocking(() -> {
      assertFalse(JavaImplicitClassIndex.getInstance().getElements("Hello", myFixture.getProject(), scope).isEmpty());
      assertTrue(JavaImplicitClassIndex.getInstance().getElements("World", myFixture.getProject(), scope).isEmpty());
    });

    WriteAction.run(() -> psiFile.getVirtualFile().rename(this, "World.java"));

    ReadAction.runBlocking(() -> {
      assertTrue(JavaImplicitClassIndex.getInstance().getElements("Hello", myFixture.getProject(), scope).isEmpty());
      assertFalse(JavaImplicitClassIndex.getInstance().getElements("World", myFixture.getProject(), scope).isEmpty());
    });
  }

  @Test
  public void test_array_type_use_annotation_stubbing() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         import java.lang.annotation.*;
                         @Target(ElementType.TYPE_USE)
                         @interface Anno { int value(); }""");

    PsiClass clazz = myFixture.addClass("""
                                          class Foo {
                                            <T> @Anno(0) String @Anno(1) [] @Anno(2) [] foo(@Anno(3) byte @Anno(4) [] data) {}
                                            List<String> @Anno(5) [] field;
                                          }""");

    ReadAction.runBlocking(() -> {
      PsiMethod method = clazz.getMethods()[0];
      PsiField field = clazz.getFields()[0];
      PsiParameter parameter = method.getParameterList().getParameters()[0];
      PsiAnnotation[] parameterAnnotations = parameter.getType().getAnnotations();
      PsiAnnotation[] parameterComponentAnnotations = ((PsiArrayType)parameter.getType()).getComponentType().getAnnotations();
      PsiAnnotation[] methodAnnotations = method.getReturnType().getAnnotations();
      PsiAnnotation[] methodComponentAnnotations = ((PsiArrayType)method.getReturnType()).getComponentType().getAnnotations();
      PsiAnnotation[] methodDeepComponentAnnotations = method.getReturnType().getDeepComponentType().getAnnotations();
      PsiAnnotation[] fieldAnnotations = field.getType().getAnnotations();

      assertFalse(((PsiFileImpl)clazz.getContainingFile()).isContentsLoaded());
      assertAnnotationValueText(methodDeepComponentAnnotations, "0");
      assertAnnotationValueText(methodAnnotations, "1");
      assertAnnotationValueText(methodComponentAnnotations, "2");
      assertAnnotationValueText(parameterComponentAnnotations, "3");
      assertAnnotationValueText(parameterAnnotations, "4");
      assertAnnotationValueText(fieldAnnotations, "5");
      assertFalse(((PsiFileImpl)clazz.getContainingFile()).isContentsLoaded());

      assertNotNull(clazz.getNode());// load AST
      assertEquals(1, parameter.getType().getAnnotations().length);
    });
  }

  @Test
  @TestFor(issues = "IDEA-393876")
  public void test_varargs_of_record_compact_constructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass cls = myFixture.addClass("""
                                        public record VarArgsRecord(String first, int... rest) {
                                          public VarArgsRecord { rest = rest.clone(); }
                                        }""");

    ReadAction.runBlocking(() -> {
      assertNotNull(((PsiFileImpl)cls.getContainingFile()).getStub());
      PsiMethod constructor = assertThat(cls.getConstructors()).singleElement().actual();
      assertTrue(constructor.isVarArgs());
      assertTrue(constructor.getParameterList().getParameters()[1].isVarArgs());
    });
  }

  @Test
  @TestFor(issues = "IDEA-393876")
  public void test_no_varargs_of_record_compact_constructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass cls = myFixture.addClass("""
                                        public record ArrayRecord(String first, int[] rest) {
                                          public ArrayRecord { rest = rest.clone(); }
                                        }""");

    ReadAction.runBlocking(() -> {
      assertNotNull(((PsiFileImpl)cls.getContainingFile()).getStub());
      PsiMethod constructor = assertThat(cls.getConstructors()).singleElement().actual();
      assertFalse(constructor.isVarArgs());
      assertFalse(constructor.getParameterList().getParameters()[1].isVarArgs());
    });
  }

  private static void assertAnnotationValueText(PsiAnnotation[] annotations, String text) {
    assertEquals(1, annotations.length);
    assertEquals(annotations[0].findAttributeValue("value").getText(), text);
  }
}
