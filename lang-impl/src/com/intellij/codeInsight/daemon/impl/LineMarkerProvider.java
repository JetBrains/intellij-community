package com.intellij.codeInsight.daemon.impl;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface LineMarkerProvider {
  @Nullable
  LineMarkerInfo getLineMarkerInfo(PsiElement element);
}
