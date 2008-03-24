package com.intellij.codeInsight.daemon.impl;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Collection;

/**
 * @author yole
 */
public interface LineMarkerProvider {
  @Nullable
  LineMarkerInfo getLineMarkerInfo(PsiElement element);

  void collectSlowLineMarkers(final List<PsiElement> elements, final Collection<LineMarkerInfo> result);
}
