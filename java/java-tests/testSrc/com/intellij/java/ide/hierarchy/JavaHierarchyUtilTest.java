// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.hierarchy;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.assertj.core.api.Assertions;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class JavaHierarchyUtilTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void testGetElementLocationPathTopLevelClass() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      package com.example;
      class <caret>MyClass {}
      """);
    assertThat(ReadAction.computeBlocking(() -> JavaHierarchyUtil.getElementLocationPath(psiClass))).isEqualTo("com.example");
  }

  @Test
  public void testGetElementLocationPathNestedClass() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      package com.example;
      class Outer {
        class <caret>Inner {}
      }
      """);
    assertThat(ReadAction.computeBlocking(() -> JavaHierarchyUtil.getElementLocationPath(psiClass))).isEqualTo("com.example.Outer");
  }

  @Test
  public void testGetElementLocationPathMethodInClass() {
    var method = getElementAtCaret(PsiMethod.class, """
      package com.example;
      class MyClass {
        void <caret>myMethod() {}
      }
      """);
    assertThat(ReadAction.computeBlocking(() -> JavaHierarchyUtil.getElementLocationPath(method))).isEqualTo("com.example.MyClass");
  }

  @Test
  public void testGetElementLocationPathMethodInAnonymousInsideMethod() {
    var method = getElementAtCaret(PsiMethod.class, """
      package com.example;
      class MyClass {
        void outerMethod() {
          Runnable r = new Runnable() {
            public void <caret>run() {}
          };
        }
      }
      """);
    assertThat(ReadAction.computeBlocking(() -> JavaHierarchyUtil.getElementLocationPath(method))).isEqualTo("com.example.MyClass.outerMethod()");
  }

  @Test
  public void testGetElementLocationPathMethodInLocalClass() {
    var method = getElementAtCaret(PsiMethod.class, """
      package com.example;
      class MyClass {
        void outerMethod() {
          class LocalClass {
            public void <caret>localClassMethod() {}
          }
        }
      }
      """);
    assertThat(ReadAction.computeBlocking(() -> JavaHierarchyUtil.getElementLocationPath(method))).isEqualTo("com.example.MyClass.outerMethod().LocalClass");
  }

  @Test
  public void testGetElementLocationPathFieldInClass() {
    var field = getElementAtCaret(PsiField.class, """
      package com.example;
      class MyClass {
        int <caret>myField;
      }
      """);
    assertThat(ReadAction.computeBlocking(() -> JavaHierarchyUtil.getElementLocationPath(field))).isEqualTo("com.example.MyClass");
  }

  @Test
  public void testGetElementLocationPathDefaultPackage() {
    var method = getElementAtCaret(PsiMethod.class, """
      class MyClass {
        void <caret>myMethod() {}
      }
      """);
    assertThat(ReadAction.computeBlocking(() -> JavaHierarchyUtil.getElementLocationPath(method))).isEqualTo("MyClass");
  }

  @Test
  public void testGetElementLocationPathDefaultPackageTopLevel() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class <caret>MyClass {}
      """);
    assertThat(ReadAction.computeBlocking(() -> JavaHierarchyUtil.getElementLocationPath(psiClass))).isEqualTo("");
  }

  private <T extends PsiElement> T getElementAtCaret(Class<T> aClass, @Language("JAVA") String text) {
    daemonFixture.get().configureFromFileText("File.java", text);
    return ReadAction.computeBlocking(() -> findElementAtCaret(aClass));
  }

  private <T extends PsiElement> T findElementAtCaret(Class<T> elementClass) {
    T element = PsiTreeUtil.getParentOfType(findElementAtCaret(), elementClass);
    if (element == null) Assertions.fail("Element at caret does not have parent of type " + elementClass.getName());
    return element;
  }

  private PsiElement findElementAtCaret() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    int offset = myFixture.getEditor().getCaretModel().getOffset();
    PsiElement psiElement = myFixture.getFile().findElementAt(offset);
    if (psiElement == null) Assertions.fail("Could not find PsiElement at caret position");
    return psiElement;
  }
}
