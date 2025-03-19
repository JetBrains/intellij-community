// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class JavaElementSignatureProviderTest extends LightJavaCodeInsightFixtureTestCase {
  public void testRobustAgainstWrongSignature() {
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
