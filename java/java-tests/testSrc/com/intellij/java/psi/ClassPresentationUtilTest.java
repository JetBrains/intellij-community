// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.assertj.core.api.Assertions;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class ClassPresentationUtilTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testGetNameForClassTopLevelClass() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class <caret>MyClass {}
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getNameForClass(psiClass, false)).isEqualTo("MyClass");
    });
  }

  @Test
  public void testGetNameForClassTopLevelClassQualified() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      package com.example;
      class <caret>MyClass {}
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getNameForClass(psiClass, true)).isEqualTo("com.example.MyClass");
    });
  }

  @Test
  public void testGetNameForClassNestedClass() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class Outer {
        class <caret>Inner {}
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getNameForClass(psiClass, false)).isEqualTo("Inner in Outer");
    });
  }

  @Test
  public void testGetNameForClassNestedClassQualified() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      package com.example;
      class Outer {
        class <caret>Inner {}
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getNameForClass(psiClass, true)).isEqualTo("com.example.Outer.Inner");
    });
  }

  @Test
  public void testGetNameForClassDoubleNestedClass() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class Outer {
        class Middle {
          class <caret>Inner {}
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getNameForClass(psiClass, false)).isEqualTo("Inner in Middle in Outer");
    });
  }

  @Test
  public void testGetNameForClassLocalClassInMethod() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class Outer {
        void myMethod() {
          class <caret>LocalClass {}
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getNameForClass(psiClass, false)).isEqualTo("LocalClass in myMethod() in Outer");
    });
  }

  @Test
  public void testGetNameForClassLocalClassInMethodQualified() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      package com.example;
      class Outer {
        void myMethod() {
          class <caret>LocalClass {}
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getNameForClass(psiClass, true)).isEqualTo("LocalClass in myMethod() in com.example.Outer");
    });
  }

  @Test
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
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getNameForClass(psiClass, false)).isEqualTo("Anonymous in myMethod() in Outer");
    });
  }

  @Test
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
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getNameForClass(psiClass, true)).isEqualTo("Anonymous in myMethod() in com.example.Outer");
    });
  }

  @Test
  public void testGetNameForClassAnonymousClassInFieldInitializer() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class Outer {
        Object field = new <caret>Object() {};
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getNameForClass(psiClass, false)).isEqualTo("Anonymous in field in Outer");
    });
  }

  @Test
  public void testGetNameForClassEnumConstantInitializer() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      enum MyEnum {
        CONST {
          @Override
          public String <caret>toString() { return ""; }
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getNameForClass(psiClass, false)).isEqualTo("Enum constant 'CONST' in 'MyEnum'");
    });
  }

  @Test
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
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getNameForClass(psiClass, true)).isEqualTo("Enum constant 'CONST' in 'com.example.MyEnum'");
    });
  }

  @Test
  public void testGetNameForClassImplicitClass() {
    codeInsightFixture.get().configureByText("File.java", """
      void main() {}
      """);
    ReadAction.runBlocking(() -> {
      PsiClass psiClass = ((PsiJavaFile)codeInsightFixture.get().getFile()).getClasses()[0];
      assertThat(ClassPresentationUtil.getNameForClass(psiClass, false)).isEqualTo("File");
    });
  }

  @Test
  public void testGetNameForClassImplicitClassQualified() {
    codeInsightFixture.get().configureByText("File.java", """
      package com.example;
      void main() {}
      """);
    ReadAction.runBlocking(() -> {
      PsiClass psiClass = ((PsiJavaFile)codeInsightFixture.get().getFile()).getClasses()[0];
      assertThat(ClassPresentationUtil.getNameForClass(psiClass, true)).isEqualTo("File");
    });
  }

  @Test
  public void testGetContextNameMethodInClass() {
    var method = getElementAtCaret(PsiMethod.class, """
      class Outer {
        void <caret>myMethod() {}
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getContextName(method, false)).isEqualTo("Outer");
    });
  }

  @Test
  public void testGetContextNameFieldInClass() {
    var field = getElementAtCaret(PsiField.class, """
      class Outer {
        int <caret>myField;
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getContextName(field, false)).isEqualTo("Outer");
    });
  }

  @Test
  public void testGetContextNameReturnsNullTopLevelClass() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class <caret>Outer {}
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getContextName(psiClass, false)).isNull();
    });
  }

  @Test
  public void testGetContextNameQualified() {
    var method = getElementAtCaret(PsiMethod.class, """
      package com.example;
      class Outer {
        void <caret>myMethod() {}
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getContextName(method, true)).isEqualTo("com.example.Outer");
    });
  }

  @Test
  public void testGetContextNameIgnorePsiClassOwnerFalse() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class <caret>Outer {}
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getContextName(psiClass, false, false)).isEqualTo("File.java");
    });
  }

  @Test
  public void testGetFunctionalExpressionPresentationLambda() {
    var lambda = getElementAtCaret(PsiLambdaExpression.class, """
      class Outer {
        void myMethod() {
          Runnable r = <caret>() -> {};
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getFunctionalExpressionPresentation(lambda, false))
        .isEqualTo("() -> {...} in myMethod() in Outer");
    });
  }

  @Test
  public void testGetFunctionalExpressionPresentationMethodReference() {
    var methodRef = getElementAtCaret(PsiMethodReferenceExpression.class, """
      class Outer {
        void myMethod() {
          Runnable r = this::<caret>toString;
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getFunctionalExpressionPresentation(methodRef, false))
        .isEqualTo("this::toString in myMethod() in Outer");
    });
  }

  @Test
  public void testGetFunctionalExpressionPresentationLambdaInFieldInitializer() {
    var lambda = getElementAtCaret(PsiLambdaExpression.class, """
      class Outer {
        Runnable r = <caret>() -> {};
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getFunctionalExpressionPresentation(lambda, false))
        .isEqualTo("() -> {...} in r in Outer");
    });
  }

  @Test
  public void testGetFunctionalExpressionPresentationLambdaQualified() {
    var lambda = getElementAtCaret(PsiLambdaExpression.class, """
      package com.example;
      class Outer {
        void myMethod() {
          Runnable r = <caret>() -> {};
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getFunctionalExpressionPresentation(lambda, true))
        .isEqualTo("() -> {...} in myMethod() in com.example.Outer");
    });
  }

  // --- getSimpleNameForClass tests ---

  @Test
  public void testGetSimpleNameForClassTopLevel() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class <caret>MyClass {}
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getSimpleNameForClass(psiClass)).isEqualTo("MyClass");
    });
  }

  @Test
  public void testGetSimpleNameForClassNested() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class Outer {
        class <caret>Inner {}
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getSimpleNameForClass(psiClass)).isEqualTo("Inner");
    });
  }

  @Test
  public void testGetSimpleNameForClassAnonymous() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      class Outer {
        void myMethod() {
          Runnable r = new <caret>Runnable() {
            public void run() {}
          };
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getSimpleNameForClass(psiClass)).isEqualTo("Anonymous class");
    });
  }

  @Test
  public void testGetSimpleNameForClassEnumConstantInitializer() {
    var psiClass = getElementAtCaret(PsiClass.class, """
      enum MyEnum {
        CONST {
          @Override
          public String <caret>toString() { return ""; }
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getSimpleNameForClass(psiClass)).isEqualTo("Enum constant 'CONST'");
    });
  }

  @Test
  public void testGetSimpleNameForClassImplicit() {
    codeInsightFixture.get().configureByText("File.java", """
      void main() {}
      """);
    ReadAction.runBlocking(() -> {
      PsiClass psiClass = ((PsiJavaFile)codeInsightFixture.get().getFile()).getClasses()[0];
      assertThat(ClassPresentationUtil.getSimpleNameForClass(psiClass)).isEqualTo("Compact source file");
    });
  }

  @Test
  public void testGetSimpleFunctionalExpressionPresentationLambda() {
    var lambda = getElementAtCaret(PsiLambdaExpression.class, """
      class Outer {
        void myMethod() {
          Runnable r = <caret>() -> {};
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getSimpleFunctionalExpressionPresentation(lambda))
        .isEqualTo("() -> {...}");
    });
  }

  @Test
  public void testGetSimpleFunctionalExpressionPresentationMethodReference() {
    var methodRef = getElementAtCaret(PsiMethodReferenceExpression.class, """
      class Outer {
        void myMethod() {
          Runnable r = this::<caret>toString;
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      assertThat(ClassPresentationUtil.getSimpleFunctionalExpressionPresentation(methodRef))
        .isEqualTo("this::toString");
    });
  }

  private <T extends PsiElement> T getElementAtCaret(Class<T> aClass, @Language("JAVA") String text) {
    codeInsightFixture.get().configureByText("File.java", text);
    return ReadAction.computeBlocking(() -> findElementAtCaret(aClass));
  }

  private <T extends PsiElement> T findElementAtCaret(Class<T> elementClass) {
    T element = PsiTreeUtil.getParentOfType(findElementAtCaret(), elementClass);
    if (element == null) Assertions.fail("Element at caret does not have parent of type " + elementClass.getName());
    return element;
  }

  private PsiElement findElementAtCaret() {
    int offset = codeInsightFixture.get().getEditor().getCaretModel().getOffset();
    PsiElement psiElement = codeInsightFixture.get().getFile().findElementAt(offset);
    if (psiElement == null) Assertions.fail("Could not find PsiElement at caret position");
    return psiElement;
  }
}
