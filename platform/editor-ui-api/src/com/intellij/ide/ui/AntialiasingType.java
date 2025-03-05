// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.openapi.editor.PlatformEditorBundle;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.ui.AATextInfo;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.util.function.Supplier;

import static org.jetbrains.annotations.Nls.Capitalization.Sentence;

public enum AntialiasingType {
  SUBPIXEL("Subpixel", () -> PlatformEditorBundle.message("settings.editor.antialiasing.subpixel"), RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB, true),
  GREYSCALE("Greyscale", () ->  PlatformEditorBundle.message("settings.editor.antialiasing.greyscale"), RenderingHints.VALUE_TEXT_ANTIALIAS_ON, true),
  OFF("No antialiasing", () -> PlatformEditorBundle.message("settings.editor.antialiasing.no.antialiasing"), RenderingHints.VALUE_TEXT_ANTIALIAS_OFF, false);

  /**
   * @deprecated Use {@link #getAATextInfoForSwingComponent} instead
   */
  @Deprecated(forRemoval = true)
  public static Object getAAHintForSwingComponent() {
    return getAATextInfoForSwingComponent();
  }

  public static @Nullable AATextInfo getAATextInfoForSwingComponent() {
    UISettings uiSettings = UISettings.getInstanceOrNull();
    if (uiSettings == null) {
      return GREYSCALE.getTextInfo();
    }
    return uiSettings.getIdeAAType().getTextInfo();
  }

  public static boolean canUseSubpixelAAForIDE() {
    return !SystemInfoRt.isMac || Boolean.getBoolean("enable.macos.ide.subpixelAA");
  }

  public static boolean canUseSubpixelAAForEditor() {
    return !SystemInfo.isMacOSBigSur || Boolean.getBoolean("enable.macos.editor.subpixelAA");
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

  private final String mySerializationName;
  private final Supplier<@Nls(capitalization = Sentence) String> myPresentableName;
  private final Object myHint;
  private final boolean isEnabled;

  AntialiasingType(@NonNls String serializationName,
                   Supplier<@Nls(capitalization = Sentence) String> presentableName,
                   Object hint,
                   boolean enabled) {
    mySerializationName = serializationName;
    myPresentableName = presentableName;
    myHint = hint;
    isEnabled = enabled;
  }

  public @Nullable AATextInfo getTextInfo() {
    return isEnabled || SystemInfo.isJetBrainsJvm ? GraphicsUtil.createAATextInfo(myHint) : null;
  }

  @Override
  public @NonNls String toString() {
    return mySerializationName;
  }

  public @Nls(capitalization = Sentence) String getPresentableName() {
    return myPresentableName.get();
  }
 }
