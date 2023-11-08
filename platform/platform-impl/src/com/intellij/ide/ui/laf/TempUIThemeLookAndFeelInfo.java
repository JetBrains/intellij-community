// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.UITheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.util.IconPathPatcher;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class TempUIThemeLookAndFeelInfo extends UIThemeLookAndFeelInfoImpl {
  private static final Logger LOG = Logger.getInstance(TempUIThemeLookAndFeelInfo.class);
  private static final @NonNls String ID = "Temp theme";

  private final @Nullable UIThemeLookAndFeelInfo previousLaf;

  public TempUIThemeLookAndFeelInfo(@NotNull UITheme theme, @Nullable UIThemeLookAndFeelInfo previousLaf) {
    super(theme);
    assert ID.equals(theme.getId());

    this.previousLaf = previousLaf instanceof TempUIThemeLookAndFeelInfo ?
                       ((TempUIThemeLookAndFeelInfo)previousLaf).getPreviousLaf() :
                       previousLaf;
  }

  public @Nullable UIThemeLookAndFeelInfo getPreviousLaf() {
    return previousLaf;
  }

  @Override
  protected @Nullable InputStream getResourceAsStream(@NotNull String path) {
    Path file = Path.of(path);
    if (Files.exists(file)) {
      try {
        return Files.newInputStream(file);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    return null;
  }

  @Override
  public void installEditorScheme(@Nullable EditorColorsScheme previousEditorColorSchemeForLaf) {
    String schemeId = getEditorSchemeId();
    if (schemeId == null) {
      return;
    }

    EditorColorsManagerImpl editorColorSchemeManager = (EditorColorsManagerImpl)EditorColorsManager.getInstance();
    editorColorSchemeManager.reloadKeepingActiveScheme();
    editorColorSchemeManager.setGlobalScheme(editorColorSchemeManager.getScheme(schemeId));
  }

  public static @NotNull UITheme loadTempTheme(@NotNull InputStream stream, @NotNull IconPathPatcher patcher) throws IOException {
    UITheme theme = UITheme.Companion.loadTempThemeFromJson(stream, ID);

    IconPathPatcher oldPatcher = theme.patcher;
    if (oldPatcher == null) {
      return theme;
    }

    theme.patcher = new IconPathPatcher() {
      @Override
      public @Nullable String patchPath(@NotNull String path, @Nullable ClassLoader classLoader) {
        String result = oldPatcher.patchPath(path, classLoader);
        return result == null ? null : patcher.patchPath(result, classLoader);
      }

      @Override
      public @Nullable ClassLoader getContextClassLoader(@NotNull String path, @Nullable ClassLoader originalClassLoader) {
        return patcher.getContextClassLoader(path, originalClassLoader);
      }
    };

    return theme;
  }
}
