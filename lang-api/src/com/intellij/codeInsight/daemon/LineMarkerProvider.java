package com.intellij.codeInsight.daemon;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public interface LineMarkerProvider {
  @Nullable
  LineMarkerInfo getLineMarkerInfo(PsiElement element);

  void collectSlowLineMarkers(final List<PsiElement> elements, final Collection<LineMarkerInfo> result);
}
