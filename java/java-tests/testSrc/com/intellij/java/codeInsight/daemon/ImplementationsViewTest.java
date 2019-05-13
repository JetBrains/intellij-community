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
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.*;

public class ImplementationsViewTest extends LightCodeInsightFixtureTestCase {
  public void testFromCompletion() {
    @Language("JAVA")
    String text = "public class Foo {\n" +
                  "    private final String text;\n" +
                  "\n" +
                  "    public Foo(String text) {\n" +
                  "//        this.text = text;\n" +
                  "    }\n" +
                  "\n" +
                  "    public Foo(int i) {\n" +
                  "    }\n" +
                  "\n" +
                  "    public static void main(String[] args) {\n" +
                  "        final Foo foo = new Foo(\"\");\n" +
                  "        foo.to<caret>\n" +
                  "    }\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public String toString() {\n" +
                  "        return \"text\";\n" +
                  "    }\n" +
                  "    public void totttt(){}" +
                  "}";
    myFixture.configureByText("a.java", text);
    myFixture.completeBasic();

    PsiElement element =
      TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert element != null;
    final String newText = ImplementationViewComponent.getNewText(element);

    assertEquals("    @Override\n" +
                 "    public String toString() {\n" +
                 "        return \"text\";\n" +
                 "    }", newText);
  }

  public void testFromEditor() {
    @Language("JAVA")
    String text = "public class Foo {\n" +
                  "    private final String text;\n" +
                  "\n" +
                  "    public Foo(String text) {\n" +
                  "//        this.text = text;\n" +
                  "    }\n" +
                  "\n" +
                  "    public Foo(int i) {\n" +
                  "    }\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public String to<caret>String() {\n" +
                  "        return \"text\";\n" +
                  "    }\n" +
                  "}";
    myFixture.configureByText("a.java", text);
    PsiElement element =
      TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert element != null;
    final String newText = ImplementationViewComponent.getNewText(element);

    assertEquals("    @Override\n" +
                 "    public String toString() {\n" +
                 "        return \"text\";\n" +
                 "    }", newText);
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
    String text = "abstract class AF<caret>oo{\n" +
                  "    abstract boolean aaa();\n" +
                  "    static class AFoo1 extends AFoo {\n" +
                  "        @Override\n" +
                  "        boolean aaa() {\n" +
                  "            return false;\n" +
                  "        }\n" +
                  "    }\n" +
                  "    static class AFoo3 extends AFoo {\n" +
                  "        @Override\n" +
                  "        boolean aaa() {\n" +
                  "            return false;\n" +
                  "        }\n" +
                  "    }\n" +
                  "    static class AFoo2 extends AFoo {\n" +
                  "        @Override\n" +
                  "        boolean aaa() {\n" +
                  "            return false;\n" +
                  "        }\n" +
                  "    }\n" +
                  "    \n" +
                  "}";
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
      assertEquals(visibleFiles[0], "a.java (AFoo)");
      Arrays.sort(visibleFiles);
      Assert.assertArrayEquals(Arrays.toString(visibleFiles),
                               new String[]{"a.java (AFoo)", "a.java (AFoo1 in AFoo)", "a.java (AFoo2 in AFoo)", "a.java (AFoo3 in AFoo)"}, visibleFiles);
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
    String text = "interface AF<caret>oo{\n" +
                  "    boolean aaa();\n" +
                  "}\n" +
                  "class AFooImpl {\n" +
                  "        {\n" +
                  "             AFoo a = () -> {return false;};\n" +
                  "        }\n" +
                  "}";
    myFixture.configureByText("a.java", text);
    PsiClass psiClass =
      (PsiClass)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert psiClass != null;
    final Collection<PsiElement> classes = getClassImplementations(psiClass);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiClass);
    all.addAll(classes);
    final ImplementationViewComponent component = createImplementationView(all);
    assertContent(component, new String[]{"a.java (AFoo)", "a.java"});
  }

  public void testInterfaceConstants() {
    @Language("JAVA")
    String text = "interface AF<caret>oo{\n" +
                  "    AFoo IMPL = new AFoo(){};\n" +
                  "    boolean aaa();\n" +
                  "}";
    myFixture.configureByText("a.java", text);
    PsiClass psiClass =
      (PsiClass)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert psiClass != null;
    final Collection<PsiElement> classes = getClassImplementations(psiClass);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiClass);
    all.addAll(classes);
    final ImplementationViewComponent component = createImplementationView(all);
    assertContent(component, new String[]{"a.java (AFoo)", "a.java (Anonymous in IMPL in AFoo)"});
  }

  public void testInterfaceMethodOfFunctionalInterface() {
    @Language("JAVA")
    String text = "interface AFoo{\n" +
                  "    boolean a<caret>aa();\n" +
                  "}\n" +
                  "class AFooImpl {\n" +
                  "        {\n" +
                  "             AFoo a = () -> {return false;};\n" +
                  "        }\n" +
                  "}";
    myFixture.configureByText("a.java", text);
    PsiMethod psiMethod =
      (PsiMethod)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert psiMethod != null;
    final Collection<PsiElement> methods = getMethodImplementations(psiMethod);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiMethod);
    all.addAll(methods);
    final ImplementationViewComponent component = createImplementationView(all);
    assertContent(component, new String[]{"a.java (AFoo)", "a.java"});
  }

  public void testOnVarKeyword() {
    myFixture.configureByText("a.java", "class a {{ v<caret>ar s = \"\";}}");
    PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());
    assertInstanceOf(element, PsiClass.class);
  }
  
  public void testDefaultMethodOfFunctionalInterface() {
    @Language("JAVA")
    String text = "interface AFoo{\n" +
                  "    default boolean a<caret>aa(){}\n" +
                  "    boolean bbb();" +
                  "}\n" +
                  "class AFooImpl {\n" +
                  "        {\n" +
                  "             AFoo a = () -> {return false;};\n" +
                  "        }\n" +
                  "}";
    myFixture.configureByText("a.java", text);
    PsiMethod psiMethod =
      (PsiMethod)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert psiMethod != null;
    final Collection<PsiElement> methods = getMethodImplementations(psiMethod);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiMethod);
    all.addAll(methods);
    final ImplementationViewComponent component = createImplementationView(all);
    assertContent(component, new String[]{"a.java (AFoo)"});
  }

  public void testMethodsInInnerClasses() {
    @Language("JAVA")
    String text = "abstract class AFoo{\n" +
                  "    abstract boolean a<caret>aa();\n" +
                  "    static class AFoo1 extends AFoo {\n" +
                  "        @Override\n" +
                  "        boolean aaa() {\n" +
                  "            return false;\n" +
                  "        }\n" +
                  "    }\n" +
                  "    static class AFoo3 extends AFoo {\n" +
                  "        @Override\n" +
                  "        boolean aaa() {\n" +
                  "            return false;\n" +
                  "        }\n" +
                  "    }\n" +
                  "    static class AFoo2 extends AFoo {\n" +
                  "        @Override\n" +
                  "        boolean aaa() {\n" +
                  "            return false;\n" +
                  "        }\n" +
                  "    }\n" +
                  "    \n" +
                  "}";
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
    assertContent(component, new String[]{"a.java (AFoo)", "a.java (AFoo1 in AFoo)", "a.java (AFoo2 in AFoo)", "a.java (AFoo3 in AFoo)"});
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
