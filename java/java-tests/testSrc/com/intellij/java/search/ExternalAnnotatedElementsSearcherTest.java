// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.search;

import com.intellij.codeInsight.ExternalAnnotationsManager;
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
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;

public class ExternalAnnotatedElementsSearcherTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // Configure the test temp dir as an annotation root so ExternalAnnotatedElementsSearcher
    // (which restricts index queries to annotation roots) can find annotations.xml files added
    // by tests via myFixture.addFileToProject.
    ModuleRootModificationUtil.updateModel(getModule(), model -> {
      JavaModuleExternalPaths ext = model.getModuleExtension(JavaModuleExternalPaths.class);
      ext.setExternalAnnotationUrls(new String[]{VfsUtilCore.pathToUrl(myFixture.getTempDirPath())});
    });
  }

  public void testSearchExternallyAnnotatedClass() {
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

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    Collection<PsiClass> result = AnnotatedElementsSearch.searchPsiClasses(annClass, scope).findAll();
    assertSize(1, result);
    assertEquals("Foo", result.iterator().next().getName());
  }

  public void testSearchExternallyAnnotatedMethod() {
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

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    Collection<PsiMethod> methods = AnnotatedElementsSearch.searchPsiMethods(annClass, scope).findAll();
    assertSize(1, methods);
    assertEquals("annotatedMethod", methods.iterator().next().getName());
  }

  public void testSearchExternallyAnnotatedField() {
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

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    Collection<PsiField> fields = AnnotatedElementsSearch.searchPsiFields(annClass, scope).findAll();
    assertSize(1, fields);
    assertEquals("annotatedField", fields.iterator().next().getName());
  }

  public void testSearchExternallyAnnotatedMethodWithParameters() {
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

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    Collection<PsiMethod> methods = AnnotatedElementsSearch.searchPsiMethods(annClass, scope).findAll();
    assertSize(1, methods);
    PsiMethod method = methods.iterator().next();
    assertEquals("process", method.getName());
    assertEquals(2, method.getParameterList().getParametersCount());
  }

  public void testSearchDoesNotReturnSourceAnnotatedElements() {
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

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    Collection<PsiMethod> methods = AnnotatedElementsSearch.searchPsiMethods(annClass, scope).findAll();
    assertSize(3, methods);
    assertContainsElements(
      ContainerUtil.map(methods, PsiMethod::getName),
      "sourceAnnotated", "externallyAnnotated", "bothAnnotated"
    );
  }

  public void testSearchRespectsTypeFilter() {
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

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());

    // Only methods
    Collection<PsiMethod> methods = AnnotatedElementsSearch.searchPsiMethods(annClass, scope).findAll();
    assertSize(1, methods);

    // Only classes
    Collection<PsiClass> classes = AnnotatedElementsSearch.searchPsiClasses(annClass, scope).findAll();
    assertSize(1, classes);

    // Only fields
    Collection<PsiField> fields = AnnotatedElementsSearch.searchPsiFields(annClass, scope).findAll();
    assertSize(1, fields);

    // All members (PsiModifierListOwner)
    Collection<? extends PsiModifierListOwner> all = AnnotatedElementsSearch.searchElements(
      annClass, scope, PsiModifierListOwner.class).findAll();
    assertSize(3, all);
  }

  public void testSearchExternallyAnnotatedParameter() {
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

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    Collection<PsiParameter> params = AnnotatedElementsSearch.searchPsiParameters(annClass, scope).findAll();
    assertSize(1, params);
    assertEquals("input", params.iterator().next().getName());
  }

  public void testSearchExternallyAnnotatedNestedClass() {
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

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    Collection<PsiClass> result = AnnotatedElementsSearch.searchPsiClasses(annClass, scope).findAll();
    assertSize(1, result);
    assertEquals("Inner", result.iterator().next().getName());
  }

  public void testSearchExternallyAnnotatedNestedClassMethod() {
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

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    Collection<PsiMethod> methods = AnnotatedElementsSearch.searchPsiMethods(annClass, scope).findAll();
    assertSize(1, methods);
    assertEquals("innerMethod", methods.iterator().next().getName());
  }

  public void testSearchExternallyAnnotatedDeeplyNestedClass() {
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

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    Collection<PsiClass> classes = AnnotatedElementsSearch.searchPsiClasses(annClass, scope).findAll();
    assertSize(1, classes);
    assertEquals("Deep", classes.iterator().next().getName());

    Collection<PsiMethod> methods = AnnotatedElementsSearch.searchPsiMethods(annClass, scope).findAll();
    assertSize(1, methods);
    assertEquals("deepMethod", methods.iterator().next().getName());
  }

  public void testExternalAnnotationsManagerFindsAnnotations() {
    // This test uses a subdirectory (/extAnno) as the annotation root to verify that
    // ExternalAnnotationsManager correctly resolves annotation roots under subdirectories.
    // This intentionally overrides the setUp() root (getTempDirPath()), since the annotation
    // root must match where the annotations.xml files are placed in this scenario.
    ModuleRootModificationUtil.updateModel(getModule(), model -> {
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
    PsiClass targetClass = JavaPsiFacade.getInstance(getProject())
      .findClass("com.example.Target", GlobalSearchScope.projectScope(getProject()));
    assertNotNull(targetClass);
    PsiMethod method = targetClass.findMethodsByName("process", false)[0];
    PsiField field = targetClass.findFieldByName("name", false);
    assertNotNull(field);

    // Verify ExternalAnnotationsManager finds annotations on each element
    ExternalAnnotationsManager manager = ExternalAnnotationsManager.getInstance(getProject());

    assertNotNull("Should find external annotation on class",
                  manager.findExternalAnnotation(targetClass, "test.MyAnnotation"));
    assertNotNull("Should find external annotation on method",
                  manager.findExternalAnnotation(method, "test.MyAnnotation"));
    assertNotNull("Should find external annotation on field",
                  manager.findExternalAnnotation(field, "test.MyAnnotation"));

    // Verify AnnotatedElementsSearch also finds all three elements
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    Collection<? extends PsiModifierListOwner> found = AnnotatedElementsSearch.searchElements(
      annClass, scope, PsiModifierListOwner.class).findAll();
    assertSize(3, found);
  }
}
