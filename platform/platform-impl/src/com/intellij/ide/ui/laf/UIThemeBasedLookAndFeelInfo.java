// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.UITheme;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.util.SVGLoader;
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
public class UIThemeBasedLookAndFeelInfo extends UIManager.LookAndFeelInfo {
  private final UITheme myTheme;
  private boolean myInitialised;

  public UIThemeBasedLookAndFeelInfo(@NotNull UITheme theme) {
    super(theme.getName(), theme.isDark() ? DarculaLaf.class.getName() : IntelliJLaf.class.getName());
    myTheme = theme;
  }

  public UITheme getTheme() {
    return myTheme;
  }

  public void installTheme(UIDefaults defaults, boolean lockEditorScheme) {
    myTheme.applyProperties(defaults);
    IconPathPatcher patcher = myTheme.getPatcher();
    if (patcher != null) {
      IconLoader.installPathPatcher(patcher);
    }
    SVGLoader.setSelectionColorPatcherProvider(myTheme.getSelectionColorPatcher());

    SVGLoader.SvgElementColorPatcherProvider colorPatcher = myTheme.getColorPatcher();
    if (colorPatcher != null) {
      SVGLoader.setColorPatcherProvider(colorPatcher);
    }

    installBackgroundImage();
    if (!lockEditorScheme) {
      installEditorScheme();
    }
    myInitialised = true;
  }

  public void uninstallTheme() {
    myInitialised = false;
    myTheme.setProviderClassLoader(null);
  }

  public boolean isInitialised() {
    return myInitialised;
  }

  protected void installEditorScheme() {
    String name = myTheme.getEditorSchemeName();
    if (name != null) {
      EditorColorsManager editorColorManager = EditorColorsManager.getInstance();
      EditorColorsScheme scheme = editorColorManager.getScheme(name);
      if (scheme != null) {
        editorColorManager.setGlobalScheme(scheme);
      }
    }
  }

  private void installBackgroundImage() {
    boolean installed = installBackgroundImage(myTheme.getBackground(), IdeBackgroundUtil.EDITOR_PROP);
    installed = installBackgroundImage(myTheme.getEmptyFrameBackground(), IdeBackgroundUtil.FRAME_PROP) || installed;
    if (installed) {
      IdeBackgroundUtil.repaintAllWindows();
    }
  }

  private boolean installBackgroundImage(@Nullable Map<String, Object> backgroundProps, String bgImageProperty) {
    Object path = backgroundProps == null ? null : backgroundProps.get("image");
    if (!(path instanceof String)) {
      return false;
    }

    try {
      Path tmpImage = FileUtil.createTempFile("ijBackgroundImage", path.toString().substring(((String)path).lastIndexOf(".")), true).toPath();
      InputStream stream = myTheme.getResourceAsStream((String)path);
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
    IconPathPatcher patcher = myTheme.getPatcher();
    if (patcher != null) {
      IconLoader.removePathPatcher(patcher);
    }
    SVGLoader.setColorPatcherProvider(null);

    unsetBackgroundProperties(IdeBackgroundUtil.EDITOR_PROP);
    unsetBackgroundProperties(IdeBackgroundUtil.FRAME_PROP);
  }

  private void unsetBackgroundProperties(String backgroundPropertyKey) {
    PropertiesComponent propertyManager = PropertiesComponent.getInstance();
    String value = propertyManager.getValue("old." + backgroundPropertyKey);
    propertyManager.unsetValue("old." + backgroundPropertyKey);
    if (value == null) {
      if (myTheme.getBackground() != null) {
        propertyManager.unsetValue(backgroundPropertyKey);
      }
    }
    else {
      propertyManager.setValue(backgroundPropertyKey, value);
    }
  }
}
