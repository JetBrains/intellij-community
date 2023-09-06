// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UITheme;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.SVGLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class UIThemeLookAndFeelInfoImpl extends UIThemeLookAndFeelInfo {
  private boolean isInitialised;

  public UIThemeLookAndFeelInfoImpl(@NotNull UITheme theme) {
    super(theme);
  }

  public void installTheme(UIDefaults defaults, boolean lockEditorScheme) {
    UITheme theme = getTheme();
    defaults.put("ui.theme.is.dark", theme.isDark());
    defaults.put("ClassLoader", theme.getProviderClassLoader());
    theme.applyProperties(defaults);
    IconPathPatcher patcher = theme.getPatcher();
    if (patcher != null) {
      IconLoader.installPathPatcher(patcher);
    }
    SVGLoader.setSelectionColorPatcherProvider(theme.getSelectionColorPatcher());

    SVGLoader.SvgElementColorPatcherProvider colorPatcher = theme.getColorPatcher();
    if (colorPatcher != null) {
      SVGLoader.setColorPatcherProvider(colorPatcher);
    }

    installBackgroundImage();
    if (!lockEditorScheme) {
      installEditorScheme();
    }
    AppUIUtil.updateForDarcula(theme.isDark());
    isInitialised = true;
  }

  public final boolean isInitialised() {
    return isInitialised;
  }

  protected @Nullable InputStream getResourceAsStream(@NotNull String path) {
    return getTheme().getProviderClassLoader().getResourceAsStream(path);
  }

  protected void installEditorScheme() {
    EditorColorsScheme scheme = LafManager.getInstance().getPreviousSchemeForLaf(this);
    EditorColorsManager editorColorManager = EditorColorsManager.getInstance();
    if (scheme == null) {
      String name = getTheme().getEditorSchemeName();
      if (name != null) {
        scheme = editorColorManager.getScheme(name);
      }
    }

    if (scheme != null) {
      editorColorManager.setGlobalScheme(scheme);
    }
  }

  private void installBackgroundImage() {
    boolean installed = installBackgroundImage(getTheme().getBackground(), IdeBackgroundUtil.EDITOR_PROP);
    installed = installBackgroundImage(getTheme().getEmptyFrameBackground(), IdeBackgroundUtil.FRAME_PROP) || installed;
    if (installed) {
      IdeBackgroundUtil.repaintAllWindows();
    }
  }

  private boolean installBackgroundImage(@NotNull Map<String, Object> backgroundProps,
                                         @NotNull @NonNls String bgImageProperty) {
    Object path = backgroundProps.get("image");
    return path instanceof String && installBackgroundImage(backgroundProps, bgImageProperty, (String)path);
  }

  private boolean installBackgroundImage(@NotNull Map<String, Object> backgroundProps,
                                         @NotNull @NonNls String bgImageProperty,
                                         @NotNull @NonNls String path) {
    try {
      Path tmpImage = FileUtil.createTempFile("ijBackgroundImage", path.substring(path.lastIndexOf(".")), true).toPath();
      InputStream stream = getResourceAsStream(path);
      if (stream == null) {
        throw new IllegalArgumentException("Can't load background: " + path);
      }

      try (stream) {
        Files.copy(stream, tmpImage, StandardCopyOption.REPLACE_EXISTING);
      }

      Object transparency = backgroundProps.get("transparency");
      String alpha = String.valueOf(transparency instanceof Integer ? (int)transparency : 15);
      String fill = parseEnumValue(backgroundProps.get("fill"), IdeBackgroundUtil.Fill.SCALE);
      String anchor = parseEnumValue(backgroundProps.get("anchor"), IdeBackgroundUtil.Anchor.CENTER);

      String spec = String.join(",", tmpImage.toString(), alpha, fill, anchor);
      PropertiesComponent propertyComponent = PropertiesComponent.getInstance();
      String currentSpec = propertyComponent.getValue(bgImageProperty);
      propertyComponent.setValue("old." + bgImageProperty, currentSpec);
      propertyComponent.setValue(bgImageProperty, spec);

      return true;
    }
    catch (IOException e) {
      Logger.getInstance(getClass()).error(e);
      return false;
    }
  }

  private static <T extends Enum<T>> String parseEnumValue(Object value, T defaultValue) {
    if (value instanceof String) {
      String name = StringUtil.toUpperCase((String)value);
      for (T t : ((Class<T>)defaultValue.getClass()).getEnumConstants()) {
        if (t.name().equals(name)) {
          return StringUtil.toLowerCase(value.toString());
        }
      }
    }
    return StringUtil.toLowerCase(defaultValue.name());
  }

  public void dispose() {
    IconPathPatcher patcher = getTheme().getPatcher();
    if (patcher != null) {
      IconLoader.removePathPatcher(patcher);
    }
    SVGLoader.setColorPatcherProvider(null);

    unsetBackgroundProperties(IdeBackgroundUtil.EDITOR_PROP);
    unsetBackgroundProperties(IdeBackgroundUtil.FRAME_PROP);

    isInitialised = false;
  }

  private void unsetBackgroundProperties(String backgroundPropertyKey) {
    PropertiesComponent propertyManager = PropertiesComponent.getInstance();
    String value = propertyManager.getValue("old." + backgroundPropertyKey);
    propertyManager.unsetValue("old." + backgroundPropertyKey);
    if (value != null) {
      propertyManager.setValue(backgroundPropertyKey, value);
    }
    else if (!getTheme().getBackground().isEmpty()) {
      propertyManager.unsetValue(backgroundPropertyKey);
    }
  }
}
