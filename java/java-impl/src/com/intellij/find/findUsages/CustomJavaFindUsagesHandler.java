package com.intellij.find.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * User: Andrey.Vokin
 * Date: 7/26/12
 */
public interface CustomJavaFindUsagesHandler {
  public void processElementUsages(@NotNull final PsiElement element, @NotNull final Processor<UsageInfo> processor, @NotNull final FindUsagesOptions options);
}
