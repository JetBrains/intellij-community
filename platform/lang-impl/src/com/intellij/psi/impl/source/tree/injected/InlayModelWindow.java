// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.VisualPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;

class InlayModelWindow implements InlayModel {
  private static final Logger LOG = Logger.getInstance(InlayModelWindow.class);

  @Nullable
  @Override
  public Inlay addInlineElement(int offset, boolean relatesToPrecedingText, @NotNull EditorCustomElementRenderer renderer) {
    logUnsupported();
    return null;
  }

  @NotNull
  @Override
  public List<Inlay> getInlineElementsInRange(int startOffset, int endOffset) {
    logUnsupported();
    return Collections.emptyList();
  }

  @Override
  public boolean hasInlineElementAt(int offset) {
    logUnsupported();
    return false;
  }

  @Override
  public boolean hasInlineElementAt(@NotNull VisualPosition visualPosition) {
    logUnsupported();
    return false;
  }

  @Nullable
  @Override
  public Inlay getElementAt(@NotNull Point point) {
    logUnsupported();
    return null;
  }

  @Override
  public void addListener(@NotNull Listener listener, @NotNull Disposable disposable) {
    logUnsupported();
  }

  private static void logUnsupported() {
    LOG.error("Inlay operations are not supported for injected editors");
  }
}
