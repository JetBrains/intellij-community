/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.application.ApplicationManager;

import java.io.IOException;

public abstract class EditorColorsManager {
  public static EditorColorsManager getInstance() {
    return ApplicationManager.getApplication().getComponent(EditorColorsManager.class);
  }

  public abstract void addColorsScheme(EditorColorsScheme scheme);

  public abstract void removeAllSchemes();

  public abstract EditorColorsScheme[] getAllSchemes();

  public abstract void setGlobalScheme(EditorColorsScheme scheme);

  public abstract EditorColorsScheme getGlobalScheme();

  public abstract EditorColorsScheme getScheme(String schemeName);

  public abstract void saveAllSchemes() throws IOException;

  public abstract boolean isDefaultScheme(EditorColorsScheme scheme);

  public abstract void addEditorColorsListener(EditorColorsListener listener);
  public abstract void removeEditorColorsListener(EditorColorsListener listener);

  public abstract boolean isUseOnlyMonospacedFonts();
  public abstract void setUseOnlyMonospacedFonts(boolean b);
}
