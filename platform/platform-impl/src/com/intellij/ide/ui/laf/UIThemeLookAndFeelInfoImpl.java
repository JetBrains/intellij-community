// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.UITheme;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.svg.SvgKt;
import com.intellij.util.SVGLoader;
import org.jetbrains.annotations.Nls;
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
import java.util.Objects;

/**
 * @author Konstantin Bulenkov
 */
public class UIThemeLookAndFeelInfoImpl extends UIManager.LookAndFeelInfo implements UIThemeLookAndFeelInfo {
  private final UITheme theme;
  private boolean isInitialized;

  public UIThemeLookAndFeelInfoImpl(@NotNull UITheme theme) {
    super(theme.getName(),
          // todo one one should be used in the future
          theme.isDark() ? "com.intellij.ide.ui.laf.darcula.DarculaLaf" : "com.intellij.ide.ui.laf.IntelliJLaf");
    this.theme = theme;
  }

  @Nls
  @Override
  public @NotNull String getName() {
    //noinspection HardCodedStringLiteral
    return Objects.requireNonNullElse(theme.getName(), theme.getId());
  }

  @Override
  public final @NotNull String getId() {
    return theme.getId();
  }

  @Override
  public final @Nullable String getAuthor() {
    return theme.getAuthor();
  }

  @Override
  public final boolean isDark() {
    return theme.isDark();
  }

  @Override
  public @Nullable String getEditorSchemeId() {
    return IdeUICustomization.getInstance()
      .getUiThemeEditorSchemeId(/* themeId = */ theme.getId(), /* editorSchemeId = */ theme.getEditorSchemeId());
  }

  public @NotNull UITheme getTheme() {
    return theme;
  }

  @Override
  public @NotNull ClassLoader getProviderClassLoader() {
    return theme.getProviderClassLoader();
  }

  @Override
  public void installTheme(UIDefaults defaults) {
    defaults.put("ui.theme.is.dark", theme.isDark());
    defaults.put("ClassLoader", theme.getProviderClassLoader());
    theme.applyTheme(defaults);
    IconPathPatcher patcher = theme.patcher;
    if (patcher != null) {
      IconLoader.installPathPatcher(patcher);
    }
    SvgKt.setSelectionColorPatcherProvider(theme.selectionColorPatcher);

    SVGLoader.SvgElementColorPatcherProvider colorPatcher = theme.getColorPatcher();
    if (colorPatcher != null) {
      SVGLoader.setColorPatcherProvider(colorPatcher);
    }

    installBackgroundImage();
    AppUIUtil.updateForDarcula(theme.isDark());
    isInitialized = true;
  }

  @Override
  public final boolean isInitialized() {
    return isInitialized;
  }

  protected @Nullable InputStream getResourceAsStream(@NotNull String path) {
    return getTheme().getProviderClassLoader().getResourceAsStream(path);
  }

  @Override
  public void installEditorScheme(@Nullable EditorColorsScheme previousEditorColorSchemeForLaf) {
    EditorColorsManager editorColorManager = EditorColorsManager.getInstance();

    EditorColorsScheme editorColorSchemeToSet = previousEditorColorSchemeForLaf;
    if (editorColorSchemeToSet == null) {
      String name = getEditorSchemeId();
      if (name != null) {
        editorColorSchemeToSet = editorColorManager.getScheme(name);
      }
    }

    if (editorColorSchemeToSet != null) {
      editorColorManager.setCurrentSchemeOnLafChange(editorColorSchemeToSet);
    }
  }

  private void installBackgroundImage() {
    boolean installed = installBackgroundImage(theme.getBackground(), IdeBackgroundUtil.EDITOR_PROP);
    installed = installBackgroundImage(theme.getEmptyFrameBackground(), IdeBackgroundUtil.FRAME_PROP) || installed;
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

  @Override
  public @NotNull UIThemeExportableBean describe() {
    return theme.describe$intellij_platform_ide_impl();
  }

  private static <T extends Enum<T>> String parseEnumValue(Object value, T defaultValue) {
    if (value instanceof String) {
      String name = Strings.toUpperCase((String)value);
      //noinspection unchecked
      for (T t : ((Class<T>)defaultValue.getClass()).getEnumConstants()) {
        if (t.name().equals(name)) {
          return Strings.toLowerCase(value.toString());
        }
      }
    }
    return Strings.toLowerCase(defaultValue.name());
  }

  @Override
  public void dispose() {
    IconPathPatcher patcher = theme.patcher;
    if (patcher != null) {
      IconLoader.removePathPatcher(patcher);
    }
    SVGLoader.setColorPatcherProvider(null);

    unsetBackgroundProperties(IdeBackgroundUtil.EDITOR_PROP);
    unsetBackgroundProperties(IdeBackgroundUtil.FRAME_PROP);

    isInitialized = false;
  }

  private void unsetBackgroundProperties(String backgroundPropertyKey) {
    PropertiesComponent propertyManager = PropertiesComponent.getInstance();
    String value = propertyManager.getValue("old." + backgroundPropertyKey);
    propertyManager.unsetValue("old." + backgroundPropertyKey);
    if (value != null) {
      propertyManager.setValue(backgroundPropertyKey, value);
    }
    else if (!theme.getBackground().isEmpty()) {
      propertyManager.unsetValue(backgroundPropertyKey);
    }
  }
}
