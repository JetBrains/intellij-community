// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.codeInsight.hint.PsiImplementationViewElement;
import com.intellij.codeInsight.navigation.ClassImplementationsSearch;
import com.intellij.codeInsight.navigation.MethodImplementationsSearch;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ImplementationsViewTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testFromCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA")
    String text = """
      public class Foo {
          private final String text;

          public Foo(String text) {
      //        this.text = text;
          }

          public Foo(int i) {
          }

          public static void main(String[] args) {
              final Foo foo = new Foo("");
              foo.to<caret>
          }

          @Override
          public String toString() {
              return "text";
          }
          public void totttt(){}}""";
    myFixture.configureByText("a.java", text);
    myFixture.completeBasic();

    ReadAction.runBlocking(() -> {
      PsiElement element =
        TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

      assert element != null;
      final String newText = ImplementationViewComponent.getNewText(element);

      assertEquals("""
                         @Override
                         public String toString() {
                             return "text";
                         }\
                     """, newText);
    });
  }

  @Test
  public void testFromEditor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA")
    String text = """
      public class Foo {
          private final String text;

          public Foo(String text) {
      //        this.text = text;
          }

          public Foo(int i) {
          }

          @Override
          public String to<caret>String() {
              return "text";
          }
      }""";
    myFixture.configureByText("a.java", text);
    ReadAction.runBlocking(() -> {
      PsiElement element =
        TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

      assert element != null;
      final String newText = ImplementationViewComponent.getNewText(element);

      assertEquals("""
                         @Override
                         public String toString() {
                             return "text";
                         }\
                     """, newText);
    });
  }

  private static Collection<PsiElement> getClassImplementations(final PsiClass psiClass) {
    return ReadAction.computeBlocking(() -> {
      CommonProcessors.CollectProcessor<PsiElement> processor = new CommonProcessors.CollectProcessor<>();
      ClassImplementationsSearch.processImplementations(psiClass, processor, psiClass.getUseScope());

      return processor.getResults();
    });
  }

  private static Collection<PsiElement> getMethodImplementations(final PsiMethod psiMethod) {
    return ReadAction.computeBlocking(() -> {
      CommonProcessors.CollectProcessor<PsiElement> processor = new CommonProcessors.CollectProcessor<>();
      MethodImplementationsSearch.processImplementations( psiMethod, processor, psiMethod.getUseScope());

      return processor.getResults();
    });
  }

  @Test
  public void testInnerClasses() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA")
    String text = """
      abstract class AF<caret>oo{
          abstract boolean aaa();
          static class AFoo1 extends AFoo {
              @Override
              boolean aaa() {
                  return false;
              }
          }
          static class AFoo3 extends AFoo {
              @Override
              boolean aaa() {
                  return false;
              }
          }
          static class AFoo2 extends AFoo {
              @Override
              boolean aaa() {
                  return false;
              }
          }
         \s
      }""";
    myFixture.configureByText("a.java", text);
    PsiClass psiClass =
      ReadAction.computeBlocking(() -> (PsiClass)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted()));

    assert psiClass != null;
    final Collection<PsiElement> classes = getClassImplementations(psiClass);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiClass);
    all.addAll(classes);
    final ImplementationViewComponent component = createImplementationView(all);
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        final String[] visibleFiles = component.getVisibleFiles();
        assertTrue(visibleFiles.length > 0);
        assertEquals("AFoo", visibleFiles[0]);
        Arrays.sort(visibleFiles);
        assertArrayEquals(new String[]{"AFoo", "AFoo1 in AFoo", "AFoo2 in AFoo", "AFoo3 in AFoo"}, visibleFiles, Arrays.toString(visibleFiles));
      }
      finally {
        component.removeNotify();
      }
    });
  }

  @NotNull
  private ImplementationViewComponent createImplementationView(List<? extends PsiElement> elements) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return EdtTestUtil.runInEdtAndGet(() -> {
      ThrowableComputable<List<PsiImplementationViewElement>, RuntimeException> computableViewElements =
        () -> ReadAction.compute(() -> ContainerUtil.map(elements, PsiImplementationViewElement::new));
      List<PsiImplementationViewElement> viewElements = ProgressManager.getInstance().runProcessWithProgressSynchronously(computableViewElements, "", false, myFixture.getProject());
      return new ImplementationViewComponent(viewElements, 0);
    });
  }

  @Test
  public void testFunctionalInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA")
    String text = """
      interface AF<caret>oo{
          boolean aaa();
      }
      class AFooImpl {
              {
                   AFoo a = () -> {return false;};
              }
      }""";
    myFixture.configureByText("a.java", text);
    PsiClass psiClass =
      ReadAction.computeBlocking(() -> (PsiClass)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted()));

    assert psiClass != null;
    final Collection<PsiElement> classes = getClassImplementations(psiClass);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiClass);
    all.addAll(classes);
    final ImplementationViewComponent component = createImplementationView(all);
    assertContent(component, new String[]{"AFoo", "a"});
  }

  @Test
  public void testInterfaceConstants() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA")
    String text = """
      interface AF<caret>oo{
          AFoo IMPL = new AFoo(){};
          boolean aaa();
      }""";
    myFixture.configureByText("a.java", text);
    PsiClass psiClass =
      ReadAction.computeBlocking(() -> (PsiClass)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted()));

    assert psiClass != null;
    final Collection<PsiElement> classes = getClassImplementations(psiClass);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiClass);
    all.addAll(classes);
    final ImplementationViewComponent component = createImplementationView(all);
    assertContent(component, new String[]{"AFoo", "Anonymous in IMPL in AFoo"});
  }

  @Test
  public void testInterfaceMethodOfFunctionalInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA")
    String text = """
      interface AFoo{
          boolean a<caret>aa();
      }
      class AFooImpl {
              {
                   AFoo a = () -> {return false;};
              }
      }""";
    myFixture.configureByText("a.java", text);
    PsiMethod psiMethod =
      ReadAction.computeBlocking(() -> (PsiMethod)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted()));

    assert psiMethod != null;
    final Collection<PsiElement> methods = getMethodImplementations(psiMethod);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiMethod);
    all.addAll(methods);
    final ImplementationViewComponent component = createImplementationView(all);
    assertContent(component, new String[]{"AFoo", "a"});
  }

  @Test
  public void testOnVarKeyword() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", "class a {{ v<caret>ar s = \"\";}}");
    PsiElement element = ReadAction.computeBlocking(() -> TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted()));
    assertInstanceOf(PsiClass.class, element);
  }
  
  @Test
  public void testDefaultMethodOfFunctionalInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA")
    String text = """
      interface AFoo{
          default boolean a<caret>aa(){}
          boolean bbb();}
      class AFooImpl {
              {
                   AFoo a = () -> {return false;};
              }
      }""";
    myFixture.configureByText("a.java", text);
    PsiMethod psiMethod =
      ReadAction.computeBlocking(() -> (PsiMethod)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted()));

    assert psiMethod != null;
    final Collection<PsiElement> methods = getMethodImplementations(psiMethod);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiMethod);
    all.addAll(methods);
    final ImplementationViewComponent component = createImplementationView(all);
    assertContent(component, new String[]{"AFoo"});
  }

  @Test
  public void testMethodsInInnerClasses() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA")
    String text = """
      abstract class AFoo{
          abstract boolean a<caret>aa();
          static class AFoo1 extends AFoo {
              @Override
              boolean aaa() {
                  return false;
              }
          }
          static class AFoo3 extends AFoo {
              @Override
              boolean aaa() {
                  return false;
              }
          }
          static class AFoo2 extends AFoo {
              @Override
              boolean aaa() {
                  return false;
              }
          }
         \s
      }""";
    myFixture.configureByText("a.java", text);
      PsiMethod psiMethod =
        ReadAction.computeBlocking(() -> (PsiMethod)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted()));

    assert psiMethod != null;
    final Collection<PsiMethod> methods = ReadAction.computeBlocking(() -> OverridingMethodsSearch.search(psiMethod).findAll());
    List<PsiMethod> all = new ArrayList<>();
    all.add(psiMethod);
    all.addAll(methods);

    //make sure they are in predefined order
    ReadAction.runBlocking(() -> Collections.sort(all, Comparator.comparing(o -> o.getContainingClass().getQualifiedName())));
    final ImplementationViewComponent component = createImplementationView(all);
    assertContent(component, new String[]{"AFoo", "AFoo1 in AFoo", "AFoo2 in AFoo", "AFoo3 in AFoo"});
  }

  private static void assertContent(ImplementationViewComponent component, String[] expects) {
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        final String[] visibleFiles = component.getVisibleFiles();
        assertArrayEquals(expects, visibleFiles, Arrays.toString(visibleFiles));
      }
      finally {
        component.removeNotify();
      }
    });
  }
}
