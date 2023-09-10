// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.ColorUtil;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class EditorColorsManager {
  public static final Topic<EditorColorsListener> TOPIC = new Topic<>(EditorColorsListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  public static final @NonNls String DEFAULT_SCHEME_NAME = "Default";

  public static final @NonNls String COLOR_SCHEME_FILE_EXTENSION = ".icls";

  public static EditorColorsManager getInstance() {
    return ApplicationManager.getApplication().getService(EditorColorsManager.class);
  }

  public abstract void addColorScheme(@NotNull EditorColorsScheme scheme);

  public abstract EditorColorsScheme @NotNull [] getAllSchemes();

  public abstract void setGlobalScheme(EditorColorsScheme scheme);

  public abstract @NotNull EditorColorsScheme getGlobalScheme();

  public abstract EditorColorsScheme getScheme(@NotNull String schemeName);

  public abstract boolean isDefaultScheme(EditorColorsScheme scheme);

  public abstract boolean isUseOnlyMonospacedFonts();

  public abstract void setUseOnlyMonospacedFonts(boolean b);

  public @NotNull EditorColorsScheme getSchemeForCurrentUITheme() {
    return getGlobalScheme();
  }

  public boolean isDarkEditor() {
    return ColorUtil.isDark(getGlobalScheme().getDefaultBackground());
  }

  /**
   * Resolves a temporary link to a bundled scheme using bundled scheme's name.
   * @param scheme The scheme with unresolved parent. The call will be ignored for other schemes.
   * @throws com.intellij.openapi.util.InvalidDataException If a referenced scheme doesn't exist or is not read-only.
   */
  public void resolveSchemeParent(@NotNull EditorColorsScheme scheme) {
  }

  /**
   * Unlike {@code SchemeManager.reload()} guarantees that the currently selected color scheme remains the same unless it has been
   * removed as a result of reload.
   */
  public void reloadKeepingActiveScheme() {
  }
}
