// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.GutterMarkWrapper;
import com.intellij.openapi.editor.ImportantNonMergeableGutterMark;
import com.intellij.openapi.editor.MergeableGutterIconRenderer;
import com.intellij.openapi.editor.MergeableGutterIconRendererProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class DiffGutterRendererMergeableRendererProvider implements MergeableGutterIconRendererProvider {

  public static class DiffMergeableGutterMarkRenderer extends GutterMarkWrapper<DiffGutterRenderer> implements ImportantNonMergeableGutterMark {

    protected DiffMergeableGutterMarkRenderer(DiffGutterRenderer originalMark) {
      super(originalMark);
    }

    @Override
    public @Nullable AnAction getClickAction() { return originalMark.getClickAction(); }

    @Override
    public boolean equals(Object obj) { return obj instanceof DiffMergeableGutterMarkRenderer && originalMark.equals(((DiffMergeableGutterMarkRenderer)obj).originalMark); }

    @Override
    public int hashCode() { return System.identityHashCode(originalMark); }

    @Override
    public int getWeight() { return 0; }
  }

  @Override
  public @Nullable MergeableGutterIconRenderer tryGetMergeableIconRenderer(@NotNull GutterMark mark) {
    if (!(mark instanceof DiffGutterRenderer)) return null;
    return new DiffMergeableGutterMarkRenderer((DiffGutterRenderer)mark);
  }
}
