/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ide;

import com.intellij.openapi.application.ApplicationManager;

import java.awt.*;
import java.awt.datatransfer.Transferable;

public abstract class CopyPasteManager {
  public static final Color CUT_COLOR = new Color(160, 160, 160);

  public static CopyPasteManager getInstance() {
    return ApplicationManager.getApplication().getComponent(CopyPasteManager.class);
  }

  public abstract void addContentChangedListener(ContentChangedListener listener);

  public abstract void removeContentChangedListener(ContentChangedListener listener);

  public abstract Transferable getContents();

  public abstract Transferable[] getAllContents();

  public abstract void setContents(Transferable content);

  public abstract boolean isCutElement(Object element);

  public interface ContentChangedListener {
    void contentChanged();
  }
}