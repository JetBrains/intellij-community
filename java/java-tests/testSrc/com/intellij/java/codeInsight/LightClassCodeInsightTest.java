// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.PackagePrefixElementFinder;
import com.intellij.psi.impl.light.LightClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class LightClassCodeInsightTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();

    // MyElementFinder provides "abc.MyInterface"
    PlatformTestUtil.maskExtensions(PsiElementFinder.EP, myFixture.getProject(), Arrays.asList(new MyElementFinder(), PackagePrefixElementFinder.getInstance(myFixture.getProject())), myFixture.getTestRootDisposable());
  }

  @Test
  public void testCustomInstanceMethodHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      PsiClass myInterface = myFixture.findClass("abc.MyInterface");
      PsiMethod[] allMethods = myInterface.getAllMethods();
      assertTrue(ContainerUtil.exists(allMethods, m -> m.getName().contains("staticMethod")));
    });

    myFixture.configureByText(JavaFileType.INSTANCE,
                              """
                                class TestMethodAccess {
                                  public static void main() {
                                    abc.MyInterface foo = new abc.MyInterface() {};
                                    foo.instanceMethod();
                                  }
                                }""");
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting(HighlightSeverity.ERROR);
    assertThat(highlightInfos).isNullOrEmpty();
  }

  @Test
  public void testCustomStaticMethodHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      PsiClass myInterface = myFixture.findClass("abc.MyInterface");
      PsiMethod[] allMethods = myInterface.getAllMethods();
      assertTrue(ContainerUtil.exists(allMethods, m -> m.getName().contains("staticMethod")));
    });

    myFixture.configureByText(JavaFileType.INSTANCE,
                              """
                                class TestMethodAccess {
                                  public static void main() {
                                    abc.MyInterface.staticMethod();
                                  }
                                }""");
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting(HighlightSeverity.ERROR);
    assertThat(highlightInfos).isNullOrEmpty();
  }

  @Test
  public void testInstanceMethodCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("TestCompletion.java",
                              """
                                public class TestCompletion {
                                  public static void main(String[] args) {
                                    MyInterface foo = null;
                                    foo.<caret>
                                  }
                                  interface MyInterface{
                                    static void staticMethod() {}
                                    void instanceMethod();
                                  }
                                }""");
    myFixture.completeBasic();
    assertContainsItems("instanceMethod");
  }

  private void assertContainsItems(String name) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    assertTrue(myFixture.getLookupElementStrings().contains(name), myFixture.getLookupElementStrings().toString());
  }

  @Test
  public void testStaticMethodCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("TestCompletion.java",
                              """
                                public class TestCompletion {
                                  public static void main(String[] args) {
                                    MyInterface.<caret>
                                  }
                                  interface MyInterface{
                                    static void staticMethod() {}
                                    void instanceMethod();
                                  }
                                }""");
    myFixture.completeBasic();
    assertContainsItems("staticMethod");
  }

  @Test
  public void testCustomInstanceMethodCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      PsiClass myInterface = myFixture.findClass("abc.MyInterface");
      PsiMethod[] allMethods = myInterface.getAllMethods();
      assertTrue(ContainerUtil.exists(allMethods, m -> m.getName().contains("staticMethod")));
    });

    myFixture.configureByText("TestCompletion.java",
                              """
                                public class TestCompletion {
                                  public static void main(String[] args) {
                                    abc.MyInterface foo = null;
                                    foo.<caret>
                                  }
                                }""");
    myFixture.completeBasic();
    assertContainsItems("instanceMethod");
  }

  @Test
  public void testCustomStaticMethodCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      PsiClass myInterface = myFixture.findClass("abc.MyInterface");
      PsiMethod[] allMethods = myInterface.getAllMethods();
      assertTrue(ContainerUtil.exists(allMethods, m -> m.getName().contains("staticMethod")));
    });

    myFixture.configureByText("TestCompletion.java",
                              """
                                public class TestCompletion {
                                  public static void main(String[] args) {
                                    abc.MyInterface.<caret>
                                  }
                                }""");
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
        """
          package abc;
          public interface MyInterface {
            static void staticMethod() {}
            default void instanceMethod() {}
          }
          """;
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