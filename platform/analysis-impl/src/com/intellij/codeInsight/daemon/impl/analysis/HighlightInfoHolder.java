// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class HighlightInfoHolder {
  private final PsiFile myContextFile;
  private final @NotNull HighlightInfoFilter @NotNull [] myFilters;
  private final AnnotationSession myAnnotationSession;
  private int myErrorCount;
  private final List<HighlightInfo> myInfos;

  public HighlightInfoHolder(@NotNull PsiFile contextFile, @NotNull HighlightInfoFilter @NotNull ... filters) {
    myContextFile = contextFile;
    myAnnotationSession = new AnnotationSessionImpl(contextFile);
    myFilters = filters;
    myInfos = new ArrayList<>(Math.max(10, contextFile.getTextLength() / 800)); // extrapolated from the most error-packed AbstractTreeUI
  }

  public @NotNull AnnotationSession getAnnotationSession() {
    return myAnnotationSession;
  }

  public boolean add(@Nullable HighlightInfo info) {
    if (info == null || !accepted(info)) return false;

    HighlightSeverity severity = info.getSeverity();
    if (severity == HighlightSeverity.ERROR) {
      myErrorCount++;
    }

    return myInfos.add(info);
  }

  public void clear() {
    myErrorCount = 0;
    myInfos.clear();
  }

  public boolean hasErrorResults() {
    return myErrorCount != 0;
  }

  public int size() {
    return myInfos.size();
  }

  public @NotNull HighlightInfo get(int i) {
    return myInfos.get(i);
  }

  public @NotNull Project getProject() {
    return myContextFile.getProject();
  }

  public @NotNull PsiFile getContextFile() {
    return myContextFile;
  }

  private boolean accepted(@NotNull HighlightInfo info) {
    for (HighlightInfoFilter filter : myFilters) {
      if (!filter.accept(info, getContextFile())) return false;
    }
    return true;
  }

  public @NotNull TextAttributesScheme getColorsScheme() {
    return key -> key.getDefaultAttributes();
  }
}
