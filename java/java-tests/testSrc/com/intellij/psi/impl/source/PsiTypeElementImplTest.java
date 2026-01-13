// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.openapi.application.WriteAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@NotNullByDefault
public class PsiTypeElementImplTest extends LightJavaCodeInsightTestCase {

  public void testAddAnnotationToSimpleType() {
    doTestAddingAnnotationToTypeAtCaret(
      """
        class AClass {
          public <caret>String foo;
        }
        """);
  }

  public void testAddAnnotationToArrayType() {
    doTestAddingAnnotationToTypeAtCaret(
      """
        class AClass {
          public String <caret>[] foo;
        }
        """);
  }

  public void testAddAnnotationToMultidimensionalArrayType() {
    doTestAddingAnnotationToTypeAtCaret(
      """
        class AClass {
          public String <caret>[][] foo;
        }
        """);
  }

  public void testAddAnnotationToVarargsType() {
    doTestAddingAnnotationToTypeAtCaret(
      """
        class AClass {
          public String foo(String <caret>... param);
        }
        """);
  }

  public void testAddAnnotationToVarargsTypeWithArrayElement() {
    doTestAddingAnnotationToTypeAtCaret(
      """
        class AClass {
          public String foo(String <caret>[]... param);
        }
        """);
  }

  private void doTestAddingAnnotationToTypeAtCaret(String classFile) {
    configureFromFileWithMyAnnotationAdded(classFile);
    PsiTypeElement typeElement = getPsiTypeElementAtCaret();
    WriteAction.run(() -> typeElement.addAnnotation("MyAnnotation"));
    assertTypeAnnotations(typeElement.getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsSimpleTypeIsAppliedToThatType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          @MyAnnotation public <caret>String foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          @MyAnnotation public <caret>String[] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          @MyAnnotation public String[<caret>] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMethodThatReturnsArrayOfGenericTypeIsAppliedToArrayComponent() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          @MyAnnotation public <caret>List<String>[] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsArrayOfGenericTypeIsNotAppliedToArrayType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          @MyAnnotation public List<String>[<caret>] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMethodThatReturnsMultidimensionalArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          @MyAnnotation public <caret>String[][][] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsMultidimensionalArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          @MyAnnotation public String[][][<caret>] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMethodThatReturnsGenericTypeIsAppliedToThatType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          @MyAnnotation public <caret>List<String> foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsGenericTypeIsNotAppliedToThatTypeComponentType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          @MyAnnotation public List< <caret>String> foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }


  public void testTypeUseAnnotationOnMethodThatReturnsNestedGenericTypeIsAppliedToOuterType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          @MyAnnotation public <caret>List<List<String>> foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsNestedGenericTypeIsNotAppliedToInnerType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          @MyAnnotation public List< <caret>List<String>> foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMethodThatReturnsNestedGenericTypeIsNotAppliedToInnermostType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          @MyAnnotation public List<List< <caret>String>> foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMethodThatReturnsPrimitiveTypeIsAppliedToThatType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          @MyAnnotation public <caret>int foo() { return 0; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsPrimitiveArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          @MyAnnotation public <caret>int[] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsPrimitiveArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          @MyAnnotation public int[<caret>] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnParameterWhichTypeIsSimpleTypeIsAppliedToThatType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          public void foo(@MyAnnotation <caret>String param) {}
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnParameterWhichTypeIsArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          public void foo(@MyAnnotation <caret>String[] array) { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnParameterWhichTypeIsArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          public void foo(@MyAnnotation String[<caret>] array) { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnParameterWhichTypeIsVarargIsAppliedToArrayComponent() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          public void foo(@MyAnnotation <caret>String... array) { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnParameterWhichTypeIsVarargIsNotAppliedToArrayType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          public void foo(@MyAnnotation String..<caret>. array) { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnFieldWhichTypeIsSimpleTypeIsAppliedToThatType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          private @MyAnnotation <caret>String field;
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnFieldWhichTypeIsArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          private @MyAnnotation <caret>String[] array;
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnFieldWhichTypeIsArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          private @MyAnnotation String[<caret>] array;
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnVariableWhichTypeIsSimpleTypeIsAppliedToThatType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          void foo() {
            @MyAnnotation <caret>String variable;
          }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnVariableWhichTypeIsArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          void foo() {
            @MyAnnotation <caret>String[] array;
          }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnVariableWhichTypeIsArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          void foo() {
            @MyAnnotation String[<caret>] array;
          }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMultiCatchIsAppliedToFirstException() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          public void foo() {
            try {
            } catch (@MyAnnotation <caret>EOFException | FileNotFoundException | ObjectStreamException e) {
            }
          }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  public void testTypeUseAnnotationOnMultiCatchIsNotAppliedToMiddleException() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          public void foo() {
            try {
            } catch (@MyAnnotation EOFException | <caret>FileNotFoundException | ObjectStreamException e) {
            }
          }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMultiCatchIsNotAppliedToLastException() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          public void foo() {
            try {
            } catch (@MyAnnotation EOFException | FileNotFoundException | <caret>ObjectStreamException e) {
            }
          }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnSingleCatchIsAppliedToException() {
    configureFromFileWithMyAnnotationAdded(
      """
        class AClass {
          public void foo() {
            try {
            } catch (@MyAnnotation <caret>EOFException e) {
            }
          }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "MyAnnotation");
  }

  private static void assertTypeAnnotations(PsiType psiType, String... annotation) {
    List<@Nullable String> annotationFqns = ContainerUtil.map(psiType.getAnnotations(), PsiAnnotation::getQualifiedName);
    assertThat(annotationFqns).containsExactlyInAnyOrder(annotation);
  }

  private PsiTypeElement getPsiTypeElementAtCaret() {
    PsiElement psiElement = findElementAtCaret();
    PsiTypeElement psiTypeElement = PsiTreeUtil.getParentOfType(psiElement, PsiTypeElement.class);
    if (psiTypeElement == null) fail("Could not find PsiTypeElement at caret position");
    return psiTypeElement;
  }

  private PsiElement findElementAtCaret() {
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement psiElement = getFile().findElementAt(offset);
    if (psiElement == null) fail("Could not find PsiElement at caret position");
    return psiElement;
  }

  private void configureFromFileWithMyAnnotationAdded(String classFile) {
    configureFromFileText(
      "AClass.java",
      """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        import java.util.List;
        
        @Target(ElementType.TYPE_USE)
        public @interface MyAnnotation {}
        
        """ + classFile
    );
  }
}

