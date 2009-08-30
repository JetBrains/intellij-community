/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.util.duplicates;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author dsl
 */
public interface MatchProvider {
  PsiElement processMatch(Match match) throws IncorrectOperationException;

  List<Match> getDuplicates();

  boolean hasDuplicates();

  @NotNull String getConfirmDuplicatePrompt(Match match);
}
