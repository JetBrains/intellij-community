// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class LightClassCodeInsightTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    // MyElementFinder provides "abc.MyInterface"
    PlatformTestUtil.maskExtensions(PsiElementFinder.EP, getProject(), Collections.singletonList(new MyElementFinder()), getTestRootDisposable());
  }

  public void testCustomInstanceMethodHighlighting() {
    PsiClass myInterface = myFixture.findClass("abc.MyInterface");
    PsiMethod[] allMethods = myInterface.getAllMethods();
    assertTrue(ContainerUtil.exists(allMethods, m -> m.getName().contains("staticMethod")));

    myFixture.configureByText(JavaFileType.INSTANCE,
                              "class TestMethodAccess {\n" +
                              "  public static void main() {\n" +
                              "    abc.MyInterface foo = new abc.MyInterface() {};\n" +
                              "    foo.instanceMethod();\n" +
                              "  }\n" +
                              "}");
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting(HighlightSeverity.ERROR);
    assertEmpty(highlightInfos);
  }

  public void testCustomStaticMethodHighlighting() {
    PsiClass myInterface = myFixture.findClass("abc.MyInterface");
    PsiMethod[] allMethods = myInterface.getAllMethods();
    assertTrue(ContainerUtil.exists(allMethods, m -> m.getName().contains("staticMethod")));

    myFixture.configureByText(JavaFileType.INSTANCE,
                              "class TestMethodAccess {\n" +
                              "  public static void main() {\n" +
                              "    abc.MyInterface.staticMethod();\n" +
                              "  }\n" +
                              "}");
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting(HighlightSeverity.ERROR);
    assertEmpty(highlightInfos);
  }

  public void testInstanceMethodCompletion() {
    myFixture.configureByText("TestCompletion.java",
                              "public class TestCompletion {\n" +
                              "  public static void main(String[] args) {\n" +
                              "    MyInterface foo = null;\n" +
                              "    foo.<caret>\n" +
                              "  }\n" +
                              "  interface MyInterface{\n" +
                              "    static void staticMethod() {}\n" +
                              "    void instanceMethod();\n" +
                              "  }\n" +
                              "}");
    myFixture.completeBasic();
    assertContainsItems("instanceMethod");
  }

  private void assertContainsItems(String name) {
    assertTrue(myFixture.getLookupElementStrings().toString(), myFixture.getLookupElementStrings().contains(name));
  }

  public void testStaticMethodCompletion() {
    myFixture.configureByText("TestCompletion.java",
                              "public class TestCompletion {\n" +
                              "  public static void main(String[] args) {\n" +
                              "    MyInterface.<caret>\n" +
                              "  }\n" +
                              "  interface MyInterface{\n" +
                              "    static void staticMethod() {}\n" +
                              "    void instanceMethod();\n" +
                              "  }\n" +
                              "}");
    myFixture.completeBasic();
    assertContainsItems("staticMethod");
  }

  public void testCustomInstanceMethodCompletion() {
    PsiClass myInterface = myFixture.findClass("abc.MyInterface");
    PsiMethod[] allMethods = myInterface.getAllMethods();
    assertTrue(ContainerUtil.exists(allMethods, m -> m.getName().contains("staticMethod")));

    myFixture.configureByText("TestCompletion.java",
                              "public class TestCompletion {\n" +
                              "  public static void main(String[] args) {\n" +
                              "    abc.MyInterface foo = null;\n" +
                              "    foo.<caret>\n" +
                              "  }\n" +
                              "}");
    myFixture.completeBasic();
    assertContainsItems("instanceMethod");
  }

  public void testCustomStaticMethodCompletion() {
    PsiClass myInterface = myFixture.findClass("abc.MyInterface");
    PsiMethod[] allMethods = myInterface.getAllMethods();
    assertTrue(ContainerUtil.exists(allMethods, m -> m.getName().contains("staticMethod")));

    myFixture.configureByText("TestCompletion.java",
                              "public class TestCompletion {\n" +
                              "  public static void main(String[] args) {\n" +
                              "    abc.MyInterface.<caret>\n" +
                              "  }\n" +
                              "}");
    myFixture.completeBasic();
    assertContainsItems("staticMethod");
  }

  private static class MyElementFinder extends PsiElementFinder {
    private static final String ABC_MY_INTERFACE = "abc.MyInterface";
    private PsiClass _myInterface;

    @Nullable
    @Override
    public PsiClass findClass(@NotNull String qn, @NotNull GlobalSearchScope scope) {
      Project project = scope.getProject();
      if (project == null) {
        return null;
      }
      if (qn.equals(ABC_MY_INTERFACE)) {
        if (_myInterface != null) {
          return _myInterface;
        }
        return _myInterface = new LightClass(createDummyJavaFile(project).getClasses()[0]);
      }
      return null;
    }

    private static PsiJavaFile createDummyJavaFile(Project project) {
      final String source =
        "package abc;\n" +
        "public interface MyInterface {\n" +
        "  static void staticMethod() {}\n" +
        "  default void instanceMethod() {}\n" +
        "}\n";
      final FileType fileType = JavaFileType.INSTANCE;
      return (PsiJavaFile)PsiFileFactory.getInstance(project).createFileFromText(
        ABC_MY_INTERFACE + '.' + fileType.getDefaultExtension(), fileType, source);
    }

    @Override
    public PsiClass @NotNull [] findClasses(@NotNull String qn, @NotNull GlobalSearchScope scope) {
      PsiClass psiClass = findClass(qn, scope);
      if (psiClass != null && psiClass == _myInterface) {
        return new PsiClass[]{psiClass};
      }
      return PsiClass.EMPTY_ARRAY;
    }
  }
}