// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.codeInsight.hint.PsiImplementationViewElement;
import com.intellij.codeInsight.navigation.ClassImplementationsSearch;
import com.intellij.codeInsight.navigation.MethodImplementationsSearch;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.*;

public class ImplementationsViewTest extends LightJavaCodeInsightFixtureTestCase {
  public void testFromCompletion() {
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
  }

  public void testFromEditor() {
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
  }

  private static Collection<PsiElement> getClassImplementations(final PsiClass psiClass) {
    CommonProcessors.CollectProcessor<PsiElement> processor = new CommonProcessors.CollectProcessor<>();
    ClassImplementationsSearch.processImplementations(psiClass, processor, psiClass.getUseScope());

    return processor.getResults();
  }

  private static Collection<PsiElement> getMethodImplementations(final PsiMethod psiMethod) {
    CommonProcessors.CollectProcessor<PsiElement> processor = new CommonProcessors.CollectProcessor<>();
    MethodImplementationsSearch.processImplementations( psiMethod, processor, psiMethod.getUseScope());

    return processor.getResults();
  }

  public void testInnerClasses() {
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
      (PsiClass)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert psiClass != null;
    final Collection<PsiElement> classes = getClassImplementations(psiClass);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiClass);
    all.addAll(classes);
    final ImplementationViewComponent component = createImplementationView(all);
    try {
      final String[] visibleFiles = component.getVisibleFiles();
      assertTrue(visibleFiles.length > 0);
      assertEquals("AFoo", visibleFiles[0]);
      Arrays.sort(visibleFiles);
      Assert.assertArrayEquals(Arrays.toString(visibleFiles),
                               new String[]{"AFoo", "AFoo1 in AFoo", "AFoo2 in AFoo", "AFoo3 in AFoo"}, visibleFiles);
    }
    finally {
      component.removeNotify();
    }
  }

  @NotNull
  private ImplementationViewComponent createImplementationView(List<? extends PsiElement> elements) {
    return new ImplementationViewComponent(ContainerUtil.map(elements, PsiImplementationViewElement::new), 0);
  }

  public void testFunctionalInterface() {
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
      (PsiClass)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert psiClass != null;
    final Collection<PsiElement> classes = getClassImplementations(psiClass);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiClass);
    all.addAll(classes);
    final ImplementationViewComponent component = createImplementationView(all);
    assertContent(component, new String[]{"AFoo", "a"});
  }

  public void testInterfaceConstants() {
    @Language("JAVA")
    String text = """
      interface AF<caret>oo{
          AFoo IMPL = new AFoo(){};
          boolean aaa();
      }""";
    myFixture.configureByText("a.java", text);
    PsiClass psiClass =
      (PsiClass)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert psiClass != null;
    final Collection<PsiElement> classes = getClassImplementations(psiClass);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiClass);
    all.addAll(classes);
    final ImplementationViewComponent component = createImplementationView(all);
    assertContent(component, new String[]{"AFoo", "Anonymous in IMPL in AFoo"});
  }

  public void testInterfaceMethodOfFunctionalInterface() {
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
      (PsiMethod)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert psiMethod != null;
    final Collection<PsiElement> methods = getMethodImplementations(psiMethod);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiMethod);
    all.addAll(methods);
    final ImplementationViewComponent component = createImplementationView(all);
    assertContent(component, new String[]{"AFoo", "a"});
  }

  public void testOnVarKeyword() {
    myFixture.configureByText("a.java", "class a {{ v<caret>ar s = \"\";}}");
    PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());
    assertInstanceOf(element, PsiClass.class);
  }
  
  public void testDefaultMethodOfFunctionalInterface() {
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
      (PsiMethod)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert psiMethod != null;
    final Collection<PsiElement> methods = getMethodImplementations(psiMethod);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiMethod);
    all.addAll(methods);
    final ImplementationViewComponent component = createImplementationView(all);
    assertContent(component, new String[]{"AFoo"});
  }

  public void testMethodsInInnerClasses() {
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
        (PsiMethod)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert psiMethod != null;
    final Collection<PsiMethod> methods = OverridingMethodsSearch.search(psiMethod).findAll();
    List<PsiMethod> all = new ArrayList<>();
    all.add(psiMethod);
    all.addAll(methods);

    //make sure they are in predefined order
    Collections.sort(all, Comparator.comparing(o -> o.getContainingClass().getQualifiedName()));
    final ImplementationViewComponent component = createImplementationView(all);
    assertContent(component, new String[]{"AFoo", "AFoo1 in AFoo", "AFoo2 in AFoo", "AFoo3 in AFoo"});
  }

  private static void assertContent(ImplementationViewComponent component, String[] expects) {
    try {
      final String[] visibleFiles = component.getVisibleFiles();
      Assert.assertArrayEquals(Arrays.toString(visibleFiles), expects, visibleFiles);
    }
    finally {
      component.removeNotify();
    }
  }
}
