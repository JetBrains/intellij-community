package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.codeInsight.navigation.ClassImplementationsSearch;
import com.intellij.codeInsight.navigation.MethodImplementationsSearch;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.CommonProcessors;
import org.junit.Assert;

import java.util.*;

/**
 * User: anna
 */
public class ImplementationsViewTest extends LightCodeInsightFixtureTestCase {
  public void testFromCompletion() {
    myFixture.configureByText("a.java", "public class Foo {\n" +
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
                                        "}");
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
    myFixture.configureByText("a.java", "public class Foo {\n" +
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
                                        "}");
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
    myFixture.configureByText("a.java", "abstract class AF<caret>oo{\n" +
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
                                        "}");
    PsiClass psiClass =
      (PsiClass)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert psiClass != null;
    final Collection<PsiElement> classes = getClassImplementations(psiClass);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiClass);
    all.addAll(classes);
    final ImplementationViewComponent component =
      new ImplementationViewComponent(all.toArray(new PsiElement[all.size()]), 0);
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

  public void testFunctionalInterface() {
    myFixture.configureByText("a.java", "interface AF<caret>oo{\n" +
                                        "    boolean aaa();\n" +
                                        "}\n" +
                                        "class AFooImpl {\n" +
                                        "        {\n" +
                                        "             AFoo a = () -> {return false;};\n" +            
                                        "        }\n" +
                                        "}");
    PsiClass psiClass =
      (PsiClass)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert psiClass != null;
    final Collection<PsiElement> classes = getClassImplementations(psiClass);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiClass);
    all.addAll(classes);
    final ImplementationViewComponent component = new ImplementationViewComponent(all.toArray(new PsiElement[all.size()]), 0);
    assertContent(component, new String[]{"a.java (AFoo)", "a.java"});
  }

  public void testInterfaceConstants() {
    myFixture.configureByText("a.java", "interface AF<caret>oo{\n" +
                                        "    AFoo IMPL = new AFoo(){};\n" +
                                        "    boolean aaa();\n" +
                                        "}");
    PsiClass psiClass =
      (PsiClass)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert psiClass != null;
    final Collection<PsiElement> classes = getClassImplementations(psiClass);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiClass);
    all.addAll(classes);
    final ImplementationViewComponent component = new ImplementationViewComponent(all.toArray(new PsiElement[all.size()]), 0);
    assertContent(component, new String[]{"a.java (AFoo)", "a.java (Anonymous in IMPL in AFoo)"});
  }

  public void testInterfaceMethodOfFunctionalInterface() {
    myFixture.configureByText("a.java", "interface AFoo{\n" +
                                        "    boolean a<caret>aa();\n" +
                                        "}\n" +
                                        "class AFooImpl {\n" +
                                        "        {\n" +
                                        "             AFoo a = () -> {return false;};\n" +            
                                        "        }\n" +
                                        "}");
    PsiMethod psiMethod =
      (PsiMethod)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert psiMethod != null;
    final Collection<PsiElement> methods = getMethodImplementations(psiMethod);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiMethod);
    all.addAll(methods);
    final ImplementationViewComponent component = new ImplementationViewComponent(all.toArray(new PsiElement[all.size()]), 0);
    assertContent(component, new String[]{"a.java (AFoo)", "a.java"});
  }

  public void testDefaultMethodOfFunctionalInterface() {
    myFixture.configureByText("a.java", "interface AFoo{\n" +
                                        "    default boolean a<caret>aa(){}\n" +
                                        "    boolean bbb();" +
                                        "}\n" +
                                        "class AFooImpl {\n" +
                                        "        {\n" +
                                        "             AFoo a = () -> {return false;};\n" +            
                                        "        }\n" +
                                        "}");
    PsiMethod psiMethod =
      (PsiMethod)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert psiMethod != null;
    final Collection<PsiElement> methods = getMethodImplementations(psiMethod);
    List<PsiElement> all = new ArrayList<>();
    all.add(psiMethod);
    all.addAll(methods);
    final ImplementationViewComponent component = new ImplementationViewComponent(all.toArray(new PsiElement[all.size()]), 0);
    assertContent(component, new String[]{"a.java (AFoo)"});
  }

  public void testMethodsInInnerClasses() {
      myFixture.configureByText("a.java", "abstract class AFoo{\n" +
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
                                          "}");
      PsiMethod psiMethod =
        (PsiMethod)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

    assert psiMethod != null;
    final Collection<PsiMethod> methods = OverridingMethodsSearch.search(psiMethod).findAll();
    List<PsiMethod> all = new ArrayList<>();
    all.add(psiMethod);
    all.addAll(methods);

    //make sure they are in predefined order
    Collections.sort(all, (o1, o2) -> o1.getContainingClass().getQualifiedName()
      .compareTo(o2.getContainingClass().getQualifiedName()));
    final ImplementationViewComponent component =
      new ImplementationViewComponent(all.toArray(new PsiElement[all.size()]), 0);
    assertContent(component, new String[]{"a.java (AFoo)", "a.java (AFoo1 in AFoo)", "a.java (AFoo2 in AFoo)", "a.java (AFoo3 in AFoo)"});
  }

  public static void assertContent(ImplementationViewComponent component, String[] expects) {
    try {
      final String[] visibleFiles = component.getVisibleFiles();
      Assert.assertArrayEquals(Arrays.toString(visibleFiles), expects, visibleFiles);
    }
    finally {
      component.removeNotify();
    }
  }
}
