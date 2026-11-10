// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.impl.OffsetsElementSignatureProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

@TestApplication
public class JavaOffsetsElementSignatureProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private final OffsetsElementSignatureProvider myProvider = new OffsetsElementSignatureProvider();

  @Test
  public void testJavaStringLiteral() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String text =
      """
        class Test {
            void test() {
                bundle.getMessage("this.is.my.key");
            }
        }""";
    myFixture.configureByText("test.java", text);

    int startOffset = text.indexOf('"');
    int endOffset = text.indexOf(')', startOffset);

    String baseSignature = String.format("e#%d#%d", startOffset, endOffset);
    ReadAction.runBlocking(() -> {
      PsiElement implicitTop = myProvider.restoreBySignature(myFixture.getFile(), baseSignature, null);
      assertNotNull(implicitTop);

      PsiElement top = myProvider.restoreBySignature(myFixture.getFile(), baseSignature + "#0", null);
      assertSame(implicitTop, top);

      PsiElement bottom = myProvider.restoreBySignature(myFixture.getFile(), baseSignature + "#1", null);
      assertNotNull(bottom);
      assertNotSame(top, bottom);
    });
  }
}
