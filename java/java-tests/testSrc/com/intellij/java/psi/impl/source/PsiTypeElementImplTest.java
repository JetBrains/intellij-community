// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.impl.source;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@NotNullByDefault
@TestApplication
public class PsiTypeElementImplTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void testAddAnnotationToSimpleType() {
    doTestAddingAnnotationToTypeAtCaret(
      """
        class AClass {
          public <caret>String foo;
        }
        """);
  }

  @Test
  public void testAddAnnotationToArrayType() {
    doTestAddingAnnotationToTypeAtCaret(
      """
        class AClass {
          public String <caret>[] foo;
        }
        """);
  }

  @Test
  public void testAddAnnotationToMultidimensionalArrayType() {
    doTestAddingAnnotationToTypeAtCaret(
      """
        class AClass {
          public String <caret>[][] foo;
        }
        """);
  }

  @Test
  public void testAddAnnotationToVarargsType() {
    doTestAddingAnnotationToTypeAtCaret(
      """
        class AClass {
          public String foo(String <caret>... param);
        }
        """);
  }

  @Test
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
    PsiTypeElement typeElement = ReadAction.computeBlocking(this::getPsiTypeElementAtCaret);
    WriteCommandAction.runWriteCommandAction(daemonFixture.get().getFixture().getProject(), () -> {
      typeElement.addAnnotation("A");
    });
    ReadAction.runBlocking(() -> assertTypeAnnotations(typeElement.getType(), "A"));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsSimpleTypeIsAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B <caret>String foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A", "B"));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsNestedTypeQualifiedWithOuterTypeIsNotAppliedToNestedType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          class Nested {}
          @A public <T> @B AClass.<caret>Nested foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsFullyQualifiedTypeIsNotAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B java.lang.<caret>String foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B <caret>String[] foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A", "B"));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsArrayTypeWithQualifiedComponentIsNotAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B java.lang.<caret>String[] foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B String[<caret>] foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsArrayOfGenericTypeIsAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B <caret>List<String>[] foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A", "B"));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsArrayOfGenericTypeIsNotAppliedToArrayType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B List<String>[<caret>] foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsMultidimensionalArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B <caret>String[][][] foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A", "B"));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsMultidimensionalArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B String[][][<caret>] foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsGenericTypeIsAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B <caret>List<String> foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A", "B"));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsGenericTypeIsNotAppliedToThatTypeComponentType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B List< <caret>String> foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }


  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsNestedGenericTypeIsAppliedToOuterType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B <caret>List<List<String>> foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A", "B"));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsNestedGenericTypeIsNotAppliedToInnerType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B List< <caret>List<String>> foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsNestedGenericTypeIsNotAppliedToInnermostType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B List<List< <caret>String>> foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsPrimitiveTypeIsAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B <caret>int foo() { return 0; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A", "B"));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsPrimitiveArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B <caret>int[] foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A", "B"));
  }

  @Test
  public void testTypeUseAnnotationOnMethodThatReturnsPrimitiveArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          @A public <T> @B int[<caret>] foo() { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnParameterWhichTypeIsSimpleTypeIsAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo(@A <caret>String param) {}
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A"));
  }

  @Test
  public void testTypeUseAnnotationOnParameterWhichTypeIsNestedTypeQualifiedWithOuterTypeIsNotAppliedToNestedType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          class Nested {}
          public void foo(@MyAnnotation AClass.<caret>Nested param) {}
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnParameterWhichTypeIsFullyQualifiedIsNotAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo(@MyAnnotation java.lang.<caret>String param) {}
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnParameterWhichTypeIsArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo(@A <caret>String[] array) { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A"));
  }

  @Test
  public void testTypeUseAnnotationOnParameterWhichTypeIsArrayTypeWithQualifiedComponentIsNotAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo(@MyAnnotation java.lang.<caret>String[] array) { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnParameterWhichTypeIsArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo(@A String[<caret>] array) { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnParameterWhichTypeIsVarargIsAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo(@A <caret>String... array) { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A"));
  }

  @Test
  public void testTypeUseAnnotationOnParameterWhichTypeIsVarargIsNotAppliedToArrayType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          public void foo(@A String..<caret>. array) { return null; }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnFieldWhichTypeIsSimpleTypeIsAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          private @A <caret>String field;
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A"));
  }

  @Test
  public void testTypeUseAnnotationOnFieldWhichTypeIsNestedTypeQualifiedWithOuterTypeIsNotAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          class Nested {}
          private @MyAnnotation AClass.<caret>Nested field;
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnFieldWhichTypeIsFullyQualifiedIsNotAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          private @MyAnnotation java.lang.<caret>String field;
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnFieldWhichTypeIsArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          private @A <caret>String[] array;
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A"));
  }

  @Test
  public void testTypeUseAnnotationOnFieldWhichTypeIsArrayTypeWithQualifiedComponentIsNotAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          private @MyAnnotation java.lang.<caret>String[] array;
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnFieldWhichTypeIsArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          private @A String[<caret>] array;
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
  public void testTypeUseAnnotationOnVariableWhichTypeIsSimpleTypeIsAppliedToThatType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          void foo() {
            @A <caret>String variable;
          }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A"));
  }

  @Test
  public void testTypeUseAnnotationOnVariableWhichTypeIsArrayTypeIsAppliedToArrayComponent() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          void foo() {
            @A <caret>String[] array;
          }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A"));
  }

  @Test
  public void testTypeUseAnnotationOnVariableWhichTypeIsArrayTypeIsNotAppliedToArrayType() {
    configureFromFileWithAnnotationsAdded(
      """
        class AClass {
          void foo() {
            @A String[<caret>] array;
          }
        }
        """);
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
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
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A"));
  }

  @Test
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
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
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
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType()));
  }

  @Test
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
    ReadAction.runBlocking(() -> assertTypeAnnotations(getPsiTypeElementAtCaret().getType(), "A"));
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
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    int offset = myFixture.getEditor().getCaretModel().getOffset();
    PsiElement psiElement = myFixture.getFile().findElementAt(offset);
    if (psiElement == null) fail("Could not find PsiElement at caret position");
    return psiElement;
  }

  private void configureFromFileWithAnnotationsAdded(String classFile) {
    daemonFixture.get().configureFromFileText(
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

