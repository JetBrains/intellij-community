package com.intellij.codeInsight.daemon.impl;

import com.intellij.psi.PsiElement;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.openapi.progress.ProcessCanceledException;

import java.util.List;

/**
 * @author cdr
 */
public interface LineMarkersProcessor {
  void addLineMarkers(List<PsiElement> elements, List<LineMarkerProvider> providers, List<LineMarkerInfo> result) throws
                                                                                                                  ProcessCanceledException;
}
