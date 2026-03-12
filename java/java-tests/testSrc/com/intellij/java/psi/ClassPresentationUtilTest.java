// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.assertj.core.api.Assertions;
import org.intellij.lang.annotations.Language;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPresentationUtilTest extends LightJavaCodeInsightTestCase {
  public void testGetNameForClassTopLevelClass() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class <caret>MyClass {}
      """);
    assertThat(ClassPresentationUtil.getNameForClass(psiClass, false)).isEqualTo("MyClass");
  }

  public void testGetNameForClassTopLevelClassQualified() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      package com.example;
      class <caret>MyClass {}
      """);
    assertThat(ClassPresentationUtil.getNameForClass(psiClass, true)).isEqualTo("com.example.MyClass");
  }

  public void testGetNameForClassNestedClass() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class Outer {
        class <caret>Inner {}
      }
      """);
    assertThat(ClassPresentationUtil.getNameForClass(psiClass, false)).isEqualTo("Inner in Outer");
  }

  public void testGetNameForClassNestedClassQualified() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      package com.example;
      class Outer {
        class <caret>Inner {}
      }
      """);
    assertThat(ClassPresentationUtil.getNameForClass(psiClass, true)).isEqualTo("com.example.Outer.Inner");
  }

  public void testGetNameForClassDoubleNestedClass() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class Outer {
        class Middle {
          class <caret>Inner {}
        }
      }
      """);
    assertThat(ClassPresentationUtil.getNameForClass(psiClass, false)).isEqualTo("Inner in Middle in Outer");
  }

  public void testGetNameForClassLocalClassInMethod() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class Outer {
        void myMethod() {
          class <caret>LocalClass {}
        }
      }
      """);
    assertThat(ClassPresentationUtil.getNameForClass(psiClass, false)).isEqualTo("LocalClass in myMethod() in Outer");
  }

  public void testGetNameForClassLocalClassInMethodQualified() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      package com.example;
      class Outer {
        void myMethod() {
          class <caret>LocalClass {}
        }
      }
      """);
    assertThat(ClassPresentationUtil.getNameForClass(psiClass, true)).isEqualTo("LocalClass in myMethod() in com.example.Outer");
  }

  public void testGetNameForClassAnonymousClassInMethod() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class Outer {
        void myMethod() {
          Runnable r = new <caret>Runnable() {
            public void run() {}
          };
        }
      }
      """);
    assertThat(ClassPresentationUtil.getNameForClass(psiClass, false)).isEqualTo("Anonymous in myMethod() in Outer");
  }

  public void testGetNameForClassAnonymousClassInMethodQualified() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      package com.example;
      class Outer {
        void myMethod() {
          Runnable r = new <caret>Runnable() {
            public void run() {}
          };
        }
      }
      """);
    assertThat(ClassPresentationUtil.getNameForClass(psiClass, true)).isEqualTo("Anonymous in myMethod() in com.example.Outer");
  }

  public void testGetNameForClassAnonymousClassInFieldInitializer() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class Outer {
        Object field = new <caret>Object() {};
      }
      """);
    assertThat(ClassPresentationUtil.getNameForClass(psiClass, false)).isEqualTo("Anonymous in field in Outer");
  }

  public void testGetNameForClassEnumConstantInitializer() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      enum MyEnum {
        CONST {
          @Override
          public String <caret>toString() { return ""; }
        }
      }
      """);
    assertThat(ClassPresentationUtil.getNameForClass(psiClass, false)).isEqualTo("Enum constant 'CONST' in 'MyEnum'");
  }

  public void testGetNameForClassEnumConstantInitializerQualified() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      package com.example;
      enum MyEnum {
        CONST {
          @Override
          public String <caret>toString() { return ""; }
        }
      }
      """);
    assertThat(ClassPresentationUtil.getNameForClass(psiClass, true)).isEqualTo("Enum constant 'CONST' in 'com.example.MyEnum'");
  }

  public void testGetNameForClassImplicitClass() {
    configureFromFileText("File.java", """
      void main() {}
      """);
    PsiClass psiClass = ((PsiJavaFile)getFile()).getClasses()[0];
    assertThat(ClassPresentationUtil.getNameForClass(psiClass, false)).isEqualTo("File");
  }

  public void testGetNameForClassImplicitClassQualified() {
    configureFromFileText("File.java", """
      package com.example;
      void main() {}
      """);
    PsiClass psiClass = ((PsiJavaFile)getFile()).getClasses()[0];
    assertThat(ClassPresentationUtil.getNameForClass(psiClass, true)).isEqualTo("File");
  }

  public void testGetContextNameMethodInClass() {
    var method = getElementAtCaret(PsiMethod.class, """
      class Outer {
        void <caret>myMethod() {}
      }
      """);
    assertThat(ClassPresentationUtil.getContextName(method, false)).isEqualTo("Outer");
  }

  public void testGetContextNameFieldInClass() {
    var field = getElementAtCaret(PsiField.class, """
      class Outer {
        int <caret>myField;
      }
      """);
    assertThat(ClassPresentationUtil.getContextName(field, false)).isEqualTo("Outer");
  }

  public void testGetContextNameReturnsNullTopLevelClass() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class <caret>Outer {}
      """);
    assertThat(ClassPresentationUtil.getContextName(psiClass, false)).isNull();
  }

  public void testGetContextNameQualified() {
    var method = getElementAtCaret(PsiMethod.class, """
      package com.example;
      class Outer {
        void <caret>myMethod() {}
      }
      """);
    assertThat(ClassPresentationUtil.getContextName(method, true)).isEqualTo("com.example.Outer");
  }

  public void testGetContextNameIgnorePsiClassOwnerFalse() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class <caret>Outer {}
      """);
    assertThat(ClassPresentationUtil.getContextName(psiClass, false, false)).isEqualTo("File.java");
  }

  public void testGetFunctionalExpressionPresentationLambda() {
    var lambda = getElementAtCaret(PsiLambdaExpression.class, """
      class Outer {
        void myMethod() {
          Runnable r = <caret>() -> {};
        }
      }
      """);
    assertThat(ClassPresentationUtil.getFunctionalExpressionPresentation(lambda, false))
      .isEqualTo("() -> {...} in myMethod() in Outer");
  }

  public void testGetFunctionalExpressionPresentationMethodReference() {
    var methodRef = getElementAtCaret(PsiMethodReferenceExpression.class, """
      class Outer {
        void myMethod() {
          Runnable r = this::<caret>toString;
        }
      }
      """);
    assertThat(ClassPresentationUtil.getFunctionalExpressionPresentation(methodRef, false))
      .isEqualTo("this::toString in myMethod() in Outer");
  }

  public void testGetFunctionalExpressionPresentationLambdaInFieldInitializer() {
    var lambda = getElementAtCaret(PsiLambdaExpression.class, """
      class Outer {
        Runnable r = <caret>() -> {};
      }
      """);
    assertThat(ClassPresentationUtil.getFunctionalExpressionPresentation(lambda, false))
      .isEqualTo("() -> {...} in r in Outer");
  }

  public void testGetFunctionalExpressionPresentationLambdaQualified() {
    var lambda = getElementAtCaret(PsiLambdaExpression.class, """
      package com.example;
      class Outer {
        void myMethod() {
          Runnable r = <caret>() -> {};
        }
      }
      """);
    assertThat(ClassPresentationUtil.getFunctionalExpressionPresentation(lambda, true))
      .isEqualTo("() -> {...} in myMethod() in com.example.Outer");
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
