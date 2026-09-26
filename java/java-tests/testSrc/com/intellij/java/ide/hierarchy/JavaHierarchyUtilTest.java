// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.hierarchy;

import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.assertj.core.api.Assertions;
import org.intellij.lang.annotations.Language;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaHierarchyUtilTest extends LightJavaCodeInsightTestCase {
  public void testGetElementLocationPathTopLevelClass() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      package com.example;
      class <caret>MyClass {}
      """);
    assertThat(JavaHierarchyUtil.getElementLocationPath(psiClass)).isEqualTo("com.example");
  }

  public void testGetElementLocationPathNestedClass() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      package com.example;
      class Outer {
        class <caret>Inner {}
      }
      """);
    assertThat(JavaHierarchyUtil.getElementLocationPath(psiClass)).isEqualTo("com.example.Outer");
  }

  public void testGetElementLocationPathMethodInClass() {
    var method = getElementAtCaret(PsiMethod.class, """
      package com.example;
      class MyClass {
        void <caret>myMethod() {}
      }
      """);
    assertThat(JavaHierarchyUtil.getElementLocationPath(method)).isEqualTo("com.example.MyClass");
  }

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
    assertThat(JavaHierarchyUtil.getElementLocationPath(method)).isEqualTo("com.example.MyClass.outerMethod()");
  }

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
    assertThat(JavaHierarchyUtil.getElementLocationPath(method)).isEqualTo("com.example.MyClass.outerMethod().LocalClass");
  }

  public void testGetElementLocationPathFieldInClass() {
    var field = getElementAtCaret(PsiField.class, """
      package com.example;
      class MyClass {
        int <caret>myField;
      }
      """);
    assertThat(JavaHierarchyUtil.getElementLocationPath(field)).isEqualTo("com.example.MyClass");
  }

  public void testGetElementLocationPathDefaultPackage() {
    var method = getElementAtCaret(PsiMethod.class, """
      class MyClass {
        void <caret>myMethod() {}
      }
      """);
    assertThat(JavaHierarchyUtil.getElementLocationPath(method)).isEqualTo("MyClass");
  }

  public void testGetElementLocationPathDefaultPackageTopLevel() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class <caret>MyClass {}
      """);
    assertThat(JavaHierarchyUtil.getElementLocationPath(psiClass)).isEqualTo("");
  }

  private <T extends PsiElement> T getElementAtCaret(Class<T> aClass, @Language("JAVA") String text) {
    configureFromFileText("File.java", text);
    return findElementAtCaret(aClass);
  }

  private <T extends PsiElement> T findElementAtCaret(Class<T> elementClass) {
    T element = PsiTreeUtil.getParentOfType(findElementAtCaret(), elementClass);
    if (element == null) Assertions.fail("Element at caret does not have parent of type " + elementClass.getName());
    return element;
  }

  private PsiElement findElementAtCaret() {
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement psiElement = getFile().findElementAt(offset);
    if (psiElement == null) Assertions.fail("Could not find PsiElement at caret position");
    return psiElement;
  }
}
