// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.openapi.application.WriteAction;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
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
    configureFromFileWithAnnotationsAdded(classFile);
    PsiTypeElement typeElement = getPsiTypeElementAtCaret();
    WriteAction.run(() -> typeElement.addAnnotation("A"));
    assertTypeAnnotations(typeElement.getType(), "A");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsSimpleTypeIsAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B <caret>String foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A", "B");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsNestedTypeQualifiedWithOuterTypeIsNotAppliedToNestedType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          class Nested {}
          @A public <T> @B AClass.<caret>Nested foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMethodThatReturnsFullyQualifiedTypeIsNotAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B java.lang.<caret>String foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMethodThatReturnsArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B <caret>String[] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A", "B");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsArrayTypeWithQualifiedComponentIsNotAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B java.lang.<caret>String[] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMethodThatReturnsArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B String[<caret>] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMethodThatReturnsArrayOfGenericTypeIsAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B <caret>List<String>[] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A", "B");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsArrayOfGenericTypeIsNotAppliedToArrayType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B List<String>[<caret>] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMethodThatReturnsMultidimensionalArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B <caret>String[][][] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A", "B");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsMultidimensionalArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B String[][][<caret>] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMethodThatReturnsGenericTypeIsAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B <caret>List<String> foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A", "B");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsGenericTypeIsNotAppliedToThatTypeComponentType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B List< <caret>String> foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }


  public void testTypeUseAnnotationOnMethodThatReturnsNestedGenericTypeIsAppliedToOuterType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B <caret>List<List<String>> foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A", "B");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsNestedGenericTypeIsNotAppliedToInnerType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B List< <caret>List<String>> foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMethodThatReturnsNestedGenericTypeIsNotAppliedToInnermostType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B List<List< <caret>String>> foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMethodThatReturnsPrimitiveTypeIsAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B <caret>int foo() { return 0; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A", "B");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsPrimitiveArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B <caret>int[] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A", "B");
  }

  public void testTypeUseAnnotationOnMethodThatReturnsPrimitiveArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B int[<caret>] foo() { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnParameterWhichTypeIsSimpleTypeIsAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo(@A <caret>String param) {}
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A");
  }

  public void testTypeUseAnnotationOnParameterWhichTypeIsNestedTypeQualifiedWithOuterTypeIsNotAppliedToNestedType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          class Nested {}
          public void foo(@MyAnnotation AClass.<caret>Nested param) {}
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnParameterWhichTypeIsFullyQualifiedIsNotAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo(@MyAnnotation java.lang.<caret>String param) {}
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnParameterWhichTypeIsArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo(@A <caret>String[] array) { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A");
  }

  public void testTypeUseAnnotationOnParameterWhichTypeIsArrayTypeWithQualifiedComponentIsNotAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo(@MyAnnotation java.lang.<caret>String[] array) { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnParameterWhichTypeIsArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo(@A String[<caret>] array) { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnParameterWhichTypeIsVarargIsAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo(@A <caret>String... array) { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A");
  }

  public void testTypeUseAnnotationOnParameterWhichTypeIsVarargIsNotAppliedToArrayType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo(@A String..<caret>. array) { return null; }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnFieldWhichTypeIsSimpleTypeIsAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          private @A <caret>String field;
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A");
  }

  public void testTypeUseAnnotationOnFieldWhichTypeIsNestedTypeQualifiedWithOuterTypeIsNotAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          class Nested {}
          private @MyAnnotation AClass.<caret>Nested field;
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnFieldWhichTypeIsFullyQualifiedIsNotAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          private @MyAnnotation java.lang.<caret>String field;
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnFieldWhichTypeIsArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          private @A <caret>String[] array;
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A");
  }

  public void testTypeUseAnnotationOnFieldWhichTypeIsArrayTypeWithQualifiedComponentIsNotAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          private @MyAnnotation java.lang.<caret>String[] array;
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnFieldWhichTypeIsArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          private @A String[<caret>] array;
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnVariableWhichTypeIsSimpleTypeIsAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          void foo() {
            @A <caret>String variable;
          }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A");
  }

  public void testTypeUseAnnotationOnVariableWhichTypeIsArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          void foo() {
            @A <caret>String[] array;
          }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A");
  }

  public void testTypeUseAnnotationOnVariableWhichTypeIsArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          void foo() {
            @A String[<caret>] array;
          }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMultiCatchIsAppliedToFirstException() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo() {
            try {
            } catch (@A <caret>EOFException | FileNotFoundException | ObjectStreamException e) {
            }
          }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A");
  }

  public void testTypeUseAnnotationOnMultiCatchIsNotAppliedToMiddleException() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo() {
            try {
            } catch (@A EOFException | <caret>FileNotFoundException | ObjectStreamException e) {
            }
          }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnMultiCatchIsNotAppliedToLastException() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo() {
            try {
            } catch (@A EOFException | FileNotFoundException | <caret>ObjectStreamException e) {
            }
          }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType());
  }

  public void testTypeUseAnnotationOnSingleCatchIsAppliedToException() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo() {
            try {
            } catch (@A <caret>EOFException e) {
            }
          }
        }
        """);
    assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A");
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

  private void configureFromFileWithAnnotationsAdded(String classFile) {
    configureFromFileText(
      "AClass.java",
      """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        import java.util.List;
        
        @Target(ElementType.TYPE_USE)
        public @interface A {}
        @Target(ElementType.TYPE_USE)
        public @interface B {}
        
        """ + classFile
    );
  }
}

