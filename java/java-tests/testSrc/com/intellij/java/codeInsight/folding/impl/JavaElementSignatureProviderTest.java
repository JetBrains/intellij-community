// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.impl.JavaElementSignatureProvider;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class JavaElementSignatureProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testRobustAgainstWrongSignature() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("test.java", "class Test {}");
    String signature = "class#null";
    LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
      @Override
      public @NotNull Set<Action> processError(@NotNull String category,
                                               @NotNull String message,
                                               String @NotNull [] details,
                                               @Nullable Throwable t) {
        assertEquals("For input string: \"null\"", message);
        assertEquals("#" + JavaElementSignatureProvider.class.getName(), category);
        return Set.of();
      }
    }, () -> {
      PsiElement object = new JavaElementSignatureProvider().restoreBySignature(myFixture.getFile(), signature, null);
      assertNull(object);
    });
  }
}
