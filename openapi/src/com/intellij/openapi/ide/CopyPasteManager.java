/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.Disposable;

import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.util.EventListener;

public abstract class CopyPasteManager {
  public static final Color CUT_COLOR = new Color(160, 160, 160);

  public static CopyPasteManager getInstance() {
    return ApplicationManager.getApplication().getComponent(CopyPasteManager.class);
  }

  public abstract void addContentChangedListener(ContentChangedListener listener);

  public abstract void addContentChangedListener(ContentChangedListener listener, Disposable parentDisposable);

  public abstract void removeContentChangedListener(ContentChangedListener listener);

  public abstract Transferable getContents();

  public abstract Transferable[] getAllContents();

  public abstract void setContents(Transferable content);

  public abstract boolean isCutElement(Object element);

  public interface ContentChangedListener extends EventListener {
    void contentChanged(final Transferable oldTransferable, final Transferable newTransferable);
  }
}