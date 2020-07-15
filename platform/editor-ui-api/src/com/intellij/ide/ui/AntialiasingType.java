// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.editor.PlatformEditorBundle;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.font.FontRenderContext;

public enum AntialiasingType {
  SUBPIXEL(PlatformEditorBundle.message("settings.editor.antialiasing.subpixel"), RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB, true),
  GREYSCALE(PlatformEditorBundle.message("settings.editor.antialiasing.greyscale"), RenderingHints.VALUE_TEXT_ANTIALIAS_ON, true),
  OFF(PlatformEditorBundle.message("settings.editor.antialiasing.no.antialiasing"), RenderingHints.VALUE_TEXT_ANTIALIAS_OFF, false);

  public static Object getAAHintForSwingComponent() {
    UISettings uiSettings = UISettings.getInstanceOrNull();
    if (uiSettings != null) {
      AntialiasingType type = uiSettings.getIdeAAType();
      return type.getTextInfo();
    }
    return GREYSCALE.getTextInfo();
  }

  public static boolean canUseSubpixelAAForIDE() {
    return !SystemInfo.isMacOSMojave || Boolean.getBoolean("enable.macos.ide.subpixelAA");
  }

  public static Object getKeyForCurrentScope(boolean inEditor) {
    UISettings uiSettings = UISettings.getInstanceOrNull();
    if (uiSettings != null) {
      AntialiasingType type = inEditor ? uiSettings.getEditorAAType() : uiSettings.getIdeAAType();
      return type.myHint;
    }
    return RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
  }

  /**
   * Updates antialiasing hint value in the given context according to application's global antialiasing settings
   */
  public static FontRenderContext updateContext(@NotNull FontRenderContext context, boolean inEditor) {
    Object aaHint = getKeyForCurrentScope(inEditor);
    return aaHint == context.getAntiAliasingHint()
           ? context : new FontRenderContext(context.getTransform(), aaHint, context.getFractionalMetricsHint());
  }

  private final String myName;
  private final Object myHint;
  private final boolean isEnabled;

  AntialiasingType(String name, Object hint, boolean enabled) {
    myName = name;
    myHint = hint;
    isEnabled = enabled;
  }

  public Object getTextInfo() {
    return isEnabled || SystemInfo.isJetBrainsJvm ? GraphicsUtil.createAATextInfo(myHint) : null;
  }

  @Override
  public String toString() {
    return myName;
  }
 }
