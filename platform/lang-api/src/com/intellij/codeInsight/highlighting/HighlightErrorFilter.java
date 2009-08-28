package com.intellij.codeInsight.highlighting;

import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiErrorElement;

/**
 * @author spleaner
 */
public abstract class HighlightErrorFilter {

  public abstract boolean shouldHighlightErrorElement(@NotNull final PsiErrorElement element);

}
