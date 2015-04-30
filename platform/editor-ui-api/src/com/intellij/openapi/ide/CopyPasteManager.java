/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.ui.Gray;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.EventListener;

public abstract class CopyPasteManager {
  public static final Color CUT_COLOR = Gray._160;

  public static CopyPasteManager getInstance() {
    return ServiceManager.getService(CopyPasteManager.class);
  }

  public abstract void addContentChangedListener(@NotNull ContentChangedListener listener);

  public abstract void addContentChangedListener(@NotNull ContentChangedListener listener, @NotNull Disposable parentDisposable);

  public abstract void removeContentChangedListener(@NotNull ContentChangedListener listener);

  public abstract boolean areDataFlavorsAvailable(@NotNull DataFlavor... flavors);

  @Nullable
  public abstract Transferable getContents();

  @Nullable
  public abstract <T> T getContents(@NotNull DataFlavor flavor);

  @NotNull
  public abstract Transferable[] getAllContents();

  public abstract void setContents(@NotNull Transferable content);

  public abstract boolean isCutElement(@Nullable Object element);

  /**
   * We support 'kill rings' at the editor, i.e. every time when subsequent adjacent regions of text are copied they are
   * combined into single compound region. Every non-adjacent change makes existing regions unable to combine.
   * <p/>
   * However, there are situations when all 'kill rings' should be stopped manually (e.g. on undo). Hence, we need
   * a handle to ask for that. This method works like such a handle.
   *
   * @see KillRingTransferable
   */
  public abstract void stopKillRings();

  public interface ContentChangedListener extends EventListener {
    void contentChanged(@Nullable final Transferable oldTransferable, final Transferable newTransferable);
  }
}