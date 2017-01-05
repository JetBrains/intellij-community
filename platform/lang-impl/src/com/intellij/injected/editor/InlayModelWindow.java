/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.injected.editor;

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

public class InlayModelWindow implements InlayModel {
  private static final Logger LOG = Logger.getInstance(InlayModelWindow.class);

  @Nullable
  @Override
  public Inlay addInlineElement(int offset, @NotNull EditorCustomElementRenderer renderer) {
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
