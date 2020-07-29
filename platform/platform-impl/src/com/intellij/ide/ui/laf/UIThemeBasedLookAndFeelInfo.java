// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.UITheme;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.util.SVGLoader;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class UIThemeBasedLookAndFeelInfo extends UIManager.LookAndFeelInfo {
  private static final String RELAUNCH_PROPERTY = "UITheme.relaunch";

  private final UITheme myTheme;
  private boolean myInitialised;

  public UIThemeBasedLookAndFeelInfo(UITheme theme) {
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

  public boolean isInitialised() {
    return myInitialised;
  }

  protected void installEditorScheme() {
    String name = myTheme.getEditorSchemeName();
    if (name != null) {
      EditorColorsManager cm = EditorColorsManager.getInstance();
      EditorColorsScheme scheme = cm.getScheme(name);
      if (scheme != null) {
        cm.setGlobalScheme(scheme);
      }
    }
    else { // Offer a new Theme based EditorColorScheme for the first time after update.
      ApplicationManager.getApplication().invokeLater(() -> {
        String themeName = myTheme.getEditorSchemeName();
        if (StringUtil.isNotEmpty(themeName)) {
          EditorColorsManager cm = EditorColorsManager.getInstance();
          EditorColorsScheme globalScheme = cm.getGlobalScheme();
          PropertiesComponent properties = PropertiesComponent.getInstance();

          EditorColorsScheme baseScheme = cm.getScheme(SchemeManager.getBaseName(globalScheme));

          if (!properties.getBoolean(RELAUNCH_PROPERTY) &&
              !SchemeManager.getBaseName(globalScheme).equals(themeName) &&
              EditorColorsScheme.DEFAULT_SCHEME_NAME.equals(baseScheme.getName())) { // is default based
            EditorColorsScheme scheme = cm.getScheme(themeName);
            if (scheme != null) {
              cm.setGlobalScheme(scheme);
            }
          }
          properties.setValue(RELAUNCH_PROPERTY, true);
        }
      });
    }
  }

  private void installBackgroundImage() {
    installBackgroundImage(myTheme.getBackground(), IdeBackgroundUtil.EDITOR_PROP);
    installBackgroundImage(myTheme.getEmptyFrameBackground(), IdeBackgroundUtil.FRAME_PROP);
  }

  private void installBackgroundImage(Map<String, Object> backgroundProps, String bgImageProperty) {
    try {
      if (backgroundProps != null) {
        Object path = backgroundProps.get("image");
        if (path instanceof String) {
          File tmpImage = FileUtil.createTempFile("ijBackgroundImage", path.toString().substring(((String)path).lastIndexOf(".")), true);
          URL resource = myTheme.getResource((String)path);
          if (resource != null) {
            try (InputStream input = myTheme.getResourceAsStream((String)path)) {
              try (FileOutputStream output = new FileOutputStream(tmpImage)) {
                FileUtil.copy(input, output);
              }
            }

            String image = tmpImage.getPath();
            Object transparency = backgroundProps.get("transparency");
            String alpha = String.valueOf(transparency instanceof Integer ? (int)transparency : 15);
            String fill = parseEnumValue(backgroundProps.get("fill"), IdeBackgroundUtil.Fill.SCALE);
            String anchor = parseEnumValue(backgroundProps.get("anchor"), IdeBackgroundUtil.Anchor.CENTER);

            String spec = StringUtil.join(new String[]{image, alpha, fill, anchor}, ",");
            String currentSpec = PropertiesComponent.getInstance().getValue(bgImageProperty);
            PropertiesComponent.getInstance().setValue("old." + bgImageProperty, currentSpec);

            PropertiesComponent.getInstance().setValue(bgImageProperty, spec);
            IdeBackgroundUtil.repaintAllWindows();
          }
          else {
            throw new IllegalArgumentException("Can't load background: " + path);
          }
        }
      }
    }
    catch (IOException e) {
      Logger.getInstance(getClass()).error(e);
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
    } else {
      propertyManager.setValue(backgroundPropertyKey, value);
    }
  }
}
