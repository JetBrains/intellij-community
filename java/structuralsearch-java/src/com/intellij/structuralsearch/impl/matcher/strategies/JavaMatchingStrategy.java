// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiElement;

/**
 * @author Bas Leijdekkers
 */
public final class JavaMatchingStrategy implements MatchingStrategy {

  private static final JavaMatchingStrategy INSTANCE = new JavaMatchingStrategy();

  private JavaMatchingStrategy() {}

  @Override
  public boolean continueMatching(final PsiElement start) {
    final Language language = start.getLanguage();
    return language == JavaLanguage.INSTANCE || "JSP".equals(language.getID());
  }

  @Override
  public boolean shouldSkip(PsiElement element, PsiElement elementToMatchWith) {
    return false;
  }

  public static MatchingStrategy getInstance() {
    return INSTANCE;
  }
}
