// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import one.util.streamex.StreamEx;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public final class PsiNewExpressionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testErrorInNew() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", """
      @interface A { }
      class Foo {
        int[] data = new <caret>@A;
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiFile file = myFixture.getFile();
      PsiAnnotation annotation = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiAnnotation.class);
      assertNotNull(annotation);
      assertNull(annotation.getOwner());
    });
  }
  
  @Test
  public void testArrayAnnotations() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", """
      @interface A { }
      @interface B { }
      @interface C { }
      @interface D { }
      class Foo {
        int[][] data = new @A <caret>int @B @C [] @D [] {};
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiFile file = myFixture.getFile();
      PsiNewExpression newExpression =
        PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiNewExpression.class);
      assertNotNull(newExpression);
      assertEquals("@A int @B @C [] @D []", newExpression.getType().getCanonicalText(true));
      Map<String, PsiAnnotation> allAnnotations =
        StreamEx.of(PsiTreeUtil.collectElementsOfType(newExpression, PsiAnnotation.class))
          .toMap(anno -> anno.getQualifiedName(), Function.identity());
      assertEquals(4, allAnnotations.size());

      Map<String, String> expectedOwnerTexts = Map.of(
        "A", "@A int",
        "B", "@A int @B @C [] @D []",
        "C", "@A int @B @C [] @D []",
        "D", "@A int @D []"
      );

      for (Map.Entry<String, String> entry : expectedOwnerTexts.entrySet()) {
        String anno = entry.getKey();
        String expectedType = entry.getValue();
        PsiAnnotationOwner owner = allAnnotations.get(anno).getOwner();
        PsiType type = assertInstanceOf(PsiType.class, owner);
        assertEquals(expectedType, type.getCanonicalText(true), anno);
      }
    });
  }
}
