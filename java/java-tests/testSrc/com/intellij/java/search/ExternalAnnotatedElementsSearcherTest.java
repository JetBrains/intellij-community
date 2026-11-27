// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.search;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.roots.JavaModuleExternalPaths;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ExternalAnnotatedElementsSearcherTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath());

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    // Configure the test temp dir as an annotation root so ExternalAnnotatedElementsSearcher
    // (which restricts index queries to annotation roots) can find annotations.xml files added
    // by tests via myFixture.addFileToProject.
    ModuleRootModificationUtil.updateModel(myFixture.getModule(), model -> {
      JavaModuleExternalPaths ext = model.getModuleExtension(JavaModuleExternalPaths.class);
      ext.setExternalAnnotationUrls(new String[]{VfsUtilCore.pathToUrl(myFixture.getTempDirPath())});
    });
  }

  @Test
  public void testSearchExternallyAnnotatedClass() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass annClass = myFixture.addClass("""
      package test;
      public @interface MyAnnotation {}
      """);

    myFixture.addClass("""
      package com.example;
      public class Foo {}
      """);

    myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="com.example.Foo">
          <annotation name="test.MyAnnotation"/>
        </item>
      </root>
      """);

    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
    Collection<PsiClass> result = ReadAction.computeBlocking(() -> AnnotatedElementsSearch.searchPsiClasses(annClass, scope).findAll());
    assertEquals(1, result.size());
    assertEquals("Foo", result.iterator().next().getName());
  }

  @Test
  public void testSearchExternallyAnnotatedMethod() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass annClass = myFixture.addClass("""
      package test;
      public @interface MyAnnotation {}
      """);

    myFixture.addClass("""
      package com.example;
      public class Foo {
        public void annotatedMethod() {}
        public void otherMethod() {}
      }
      """);

    myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="com.example.Foo void annotatedMethod()">
          <annotation name="test.MyAnnotation"/>
        </item>
      </root>
      """);

    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
    Collection<PsiMethod> methods = ReadAction.computeBlocking(() -> AnnotatedElementsSearch.searchPsiMethods(annClass, scope).findAll());
    assertEquals(1, methods.size());
    assertEquals("annotatedMethod", methods.iterator().next().getName());
  }

  @Test
  public void testSearchExternallyAnnotatedField() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass annClass = myFixture.addClass("""
      package test;
      public @interface MyAnnotation {}
      """);

    myFixture.addClass("""
      package com.example;
      public class Foo {
        public String annotatedField;
        public int otherField;
      }
      """);

    myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="com.example.Foo annotatedField">
          <annotation name="test.MyAnnotation"/>
        </item>
      </root>
      """);

    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
    Collection<PsiField> fields = ReadAction.computeBlocking(() -> AnnotatedElementsSearch.searchPsiFields(annClass, scope).findAll());
    assertEquals(1, fields.size());
    assertEquals("annotatedField", fields.iterator().next().getName());
  }

  @Test
  public void testSearchExternallyAnnotatedMethodWithParameters() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass annClass = myFixture.addClass("""
      package test;
      public @interface MyAnnotation {}
      """);

    myFixture.addClass("""
      package com.example;
      public class Foo {
        public void process(String input, int count) {}
        public void process(String input) {}
      }
      """);

    myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="com.example.Foo void process(java.lang.String, int)">
          <annotation name="test.MyAnnotation"/>
        </item>
      </root>
      """);

    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
    Collection<PsiMethod> methods = ReadAction.computeBlocking(() -> AnnotatedElementsSearch.searchPsiMethods(annClass, scope).findAll());
    assertEquals(1, methods.size());
    PsiMethod method = methods.iterator().next();
    assertEquals("process", method.getName());
    assertEquals(2, method.getParameterList().getParametersCount());
  }

  @Test
  public void testSearchDoesNotReturnSourceAnnotatedElements() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    // Source annotations are handled by the existing AnnotatedElementsSearcher.
    // This test verifies that external search doesn't produce duplicates for source-annotated elements.
    PsiClass annClass = myFixture.addClass("""
      package test;
      public @interface MyAnnotation {}
      """);

    myFixture.addClass("""
      package com.example;
      public class Foo {
        @test.MyAnnotation
        public void sourceAnnotated() {}
        public void externallyAnnotated() {}
        @test.MyAnnotation
        public void bothAnnotated() {}
      }
      """);

    myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="com.example.Foo void externallyAnnotated()">
          <annotation name="test.MyAnnotation"/>
        </item>
        <item name="com.example.Foo void bothAnnotated()">
          <annotation name="test.MyAnnotation"/>
        </item>
      </root>
      """);

    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
    Collection<PsiMethod> methods = ReadAction.computeBlocking(() -> AnnotatedElementsSearch.searchPsiMethods(annClass, scope).findAll());
    assertEquals(3, methods.size());
    assertThat(ContainerUtil.map(methods, PsiMethod::getName)).contains("sourceAnnotated", "externallyAnnotated", "bothAnnotated");
  }

  @Test
  public void testSearchRespectsTypeFilter() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass annClass = myFixture.addClass("""
      package test;
      public @interface MyAnnotation {}
      """);

    myFixture.addClass("""
      package com.example;
      public class Foo {
        public String myField;
        public void myMethod() {}
      }
      """);

    myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="com.example.Foo">
          <annotation name="test.MyAnnotation"/>
        </item>
        <item name="com.example.Foo myField">
          <annotation name="test.MyAnnotation"/>
        </item>
        <item name="com.example.Foo void myMethod()">
          <annotation name="test.MyAnnotation"/>
        </item>
      </root>
      """);

    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());

    // Only methods
    Collection<PsiMethod> methods = ReadAction.computeBlocking(() -> AnnotatedElementsSearch.searchPsiMethods(annClass, scope).findAll());
    assertEquals(1, methods.size());

    // Only classes
    Collection<PsiClass> classes = ReadAction.computeBlocking(() -> AnnotatedElementsSearch.searchPsiClasses(annClass, scope).findAll());
    assertEquals(1, classes.size());

    // Only fields
    Collection<PsiField> fields = ReadAction.computeBlocking(() -> AnnotatedElementsSearch.searchPsiFields(annClass, scope).findAll());
    assertEquals(1, fields.size());

    // All members (PsiModifierListOwner)
    Collection<? extends PsiModifierListOwner> all = ReadAction.computeBlocking(() -> AnnotatedElementsSearch.searchElements(
      annClass, scope, PsiModifierListOwner.class).findAll());
    assertEquals(3, all.size());
  }

  @Test
  public void testSearchExternallyAnnotatedParameter() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass annClass = myFixture.addClass("""
      package test;
      public @interface MyAnnotation {}
      """);

    myFixture.addClass("""
      package com.example;
      public class Foo {
        public void process(String input, int count) {}
      }
      """);

    myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="com.example.Foo void process(java.lang.String, int) 0">
          <annotation name="test.MyAnnotation"/>
        </item>
      </root>
      """);

    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
    Collection<PsiParameter> params =
      ReadAction.computeBlocking(() -> AnnotatedElementsSearch.searchPsiParameters(annClass, scope).findAll());
    assertEquals(1, params.size());
    assertEquals("input", params.iterator().next().getName());
  }

  @Test
  public void testSearchExternallyAnnotatedNestedClass() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass annClass = myFixture.addClass("""
      package test;
      public @interface MyAnnotation {}
      """);

    myFixture.addClass("""
      package com.example;
      public class Outer {
        public static class Inner {}
      }
      """);

    myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="com.example.Outer.Inner">
          <annotation name="test.MyAnnotation"/>
        </item>
      </root>
      """);

    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
    Collection<PsiClass> result = ReadAction.computeBlocking(() -> AnnotatedElementsSearch.searchPsiClasses(annClass, scope).findAll());
    assertEquals(1, result.size());
    assertEquals("Inner", result.iterator().next().getName());
  }

  @Test
  public void testSearchExternallyAnnotatedNestedClassMethod() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass annClass = myFixture.addClass("""
      package test;
      public @interface MyAnnotation {}
      """);

    myFixture.addClass("""
      package com.example;
      public class Outer {
        public static class Inner {
          public void innerMethod() {}
        }
      }
      """);

    myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="com.example.Outer.Inner void innerMethod()">
          <annotation name="test.MyAnnotation"/>
        </item>
      </root>
      """);

    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
    Collection<PsiMethod> methods = ReadAction.computeBlocking(() -> AnnotatedElementsSearch.searchPsiMethods(annClass, scope).findAll());
    assertEquals(1, methods.size());
    assertEquals("innerMethod", methods.iterator().next().getName());
  }

  @Test
  public void testSearchExternallyAnnotatedDeeplyNestedClass() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass annClass = myFixture.addClass("""
      package test;
      public @interface MyAnnotation {}
      """);

    myFixture.addClass("""
      package com.example;
      public class Outer {
        public static class Middle {
          public static class Deep {
            public void deepMethod() {}
          }
        }
      }
      """);

    myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="com.example.Outer.Middle.Deep">
          <annotation name="test.MyAnnotation"/>
        </item>
        <item name="com.example.Outer.Middle.Deep void deepMethod()">
          <annotation name="test.MyAnnotation"/>
        </item>
      </root>
      """);

    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
    Collection<PsiClass> classes = ReadAction.computeBlocking(() -> AnnotatedElementsSearch.searchPsiClasses(annClass, scope).findAll());
    assertEquals(1, classes.size());
    assertEquals("Deep", classes.iterator().next().getName());

    Collection<PsiMethod> methods = ReadAction.computeBlocking(() -> AnnotatedElementsSearch.searchPsiMethods(annClass, scope).findAll());
    assertEquals(1, methods.size());
    assertEquals("deepMethod", methods.iterator().next().getName());
  }

  @Test
  public void testSearchExternallyAnnotatedGenericClass() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass annClass = myFixture.addClass("""
      package test;
      public @interface MyAnnotation {}
      """);

    myFixture.addClass("""
      package com.example;
      public class Box<T> {}
      """);

    myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="com.example.Box &lt;T&gt;">
          <annotation name="test.MyAnnotation"/>
        </item>
      </root>
      """);

    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
    Collection<PsiClass> result = ReadAction.computeBlocking(() -> AnnotatedElementsSearch.searchPsiClasses(annClass, scope).findAll());
    assertEquals(1, result.size());
    assertEquals("Box", result.iterator().next().getName());
  }

  @Test
  public void testExternalAnnotationsManagerFindsAnnotations() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    // This test uses a subdirectory (/extAnno) as the annotation root to verify that
    // ExternalAnnotationsManager correctly resolves annotation roots under subdirectories.
    // This intentionally overrides the setUp() root (getTempDirPath()), since the annotation
    // root must match where the annotations.xml files are placed in this scenario.
    ModuleRootModificationUtil.updateModel(myFixture.getModule(), model -> {
      JavaModuleExternalPaths ext = model.getModuleExtension(JavaModuleExternalPaths.class);
      ext.setExternalAnnotationUrls(new String[]{
        VfsUtilCore.pathToUrl(myFixture.getTempDirPath() + "/extAnno")
      });
    });

    // Create annotation and target classes
    PsiClass annClass = myFixture.addClass("""
      package test;
      public @interface MyAnnotation {}
      """);

    myFixture.addClass("""
      package com.example;
      public class Target {
        public String name;
        public void process(String input) {}
      }
      """);

    // Place annotations.xml under the external annotations root with proper package structure
    myFixture.addFileToProject("extAnno/com/example/annotations.xml", """
      <root>
        <item name="com.example.Target">
          <annotation name="test.MyAnnotation"/>
        </item>
        <item name="com.example.Target name">
          <annotation name="test.MyAnnotation"/>
        </item>
        <item name="com.example.Target void process(java.lang.String)">
          <annotation name="test.MyAnnotation"/>
        </item>
      </root>
      """);

    // Resolve PSI elements
    PsiClass targetClass = ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(myFixture.getProject())
      .findClass("com.example.Target", GlobalSearchScope.projectScope(myFixture.getProject())));
    assertNotNull(targetClass);
    ReadAction.runBlocking(() -> {
      PsiMethod method = targetClass.findMethodsByName("process", false)[0];
      PsiField field = targetClass.findFieldByName("name", false);
      assertNotNull(field);

      // Verify ExternalAnnotationsManager finds annotations on each element
      ExternalAnnotationsManager manager = ExternalAnnotationsManager.getInstance(myFixture.getProject());

      assertNotNull(manager.findExternalAnnotation(targetClass, "test.MyAnnotation"),
                    "Should find external annotation on class");
      assertNotNull(manager.findExternalAnnotation(method, "test.MyAnnotation"),
                    "Should find external annotation on method");
      assertNotNull(manager.findExternalAnnotation(field, "test.MyAnnotation"),
                    "Should find external annotation on field");

      // Verify AnnotatedElementsSearch also finds all three elements
      GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
      Collection<? extends PsiModifierListOwner> found = AnnotatedElementsSearch.searchElements(
        annClass, scope, PsiModifierListOwner.class).findAll();
      assertEquals(3, found.size());
    });
  }
}
