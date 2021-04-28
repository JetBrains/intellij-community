// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBusConnection;
import com.jediterm.terminal.emulator.ColorPalette;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class TerminalUiSettingsManager implements Disposable {

  private @NotNull EditorColorsScheme myColorsScheme;
  private int myFontSize;
  private JBTerminalSchemeColorPalette myColorPalette;
  private final List<JBTerminalPanel> myTerminalPanels = new CopyOnWriteArrayList<>();

  TerminalUiSettingsManager() {
    myColorsScheme = EditorColorsManager.getInstance().getGlobalScheme();

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);
    connection.subscribe(UISettingsListener.TOPIC, uiSettings -> {
      setFontSize(detectFontSize());
    });
    connection.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
      @Override
      public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
        myColorsScheme = scheme != null ? scheme : EditorColorsManager.getInstance().getGlobalScheme();
        myColorPalette = null;
        fireFontChanged();
      }
    });
  }

  @NotNull Color getDefaultForeground() {
    Color foregroundColor = myColorsScheme.getAttributes(ConsoleViewContentType.NORMAL_OUTPUT_KEY).getForegroundColor();
    return foregroundColor != null ? foregroundColor : myColorsScheme.getDefaultForeground();
  }

  @NotNull Color getDefaultBackground() {
    Color color = myColorsScheme.getColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY);
    return color != null ? color : myColorsScheme.getDefaultBackground();
  }

  static @NotNull TerminalUiSettingsManager getInstance() {
    return ApplicationManager.getApplication().getService(TerminalUiSettingsManager.class);
  }

  void addListener(@NotNull JBTerminalPanel terminalPanel) {
    myTerminalPanels.add(terminalPanel);
    Disposer.register(terminalPanel, () -> myTerminalPanels.remove(terminalPanel));
  }

  private void fireFontChanged() {
    for (JBTerminalPanel panel : myTerminalPanels) {
      panel.fontChanged();
    }
  }

  @NotNull EditorColorsScheme getEditorColorsScheme() {
    return myColorsScheme;
  }

  @NotNull ColorPalette getTerminalColorPalette() {
    JBTerminalSchemeColorPalette colorPalette = myColorPalette;
    if (colorPalette == null) {
      colorPalette = new JBTerminalSchemeColorPalette(myColorsScheme);
      myColorPalette = colorPalette;
    }
    return colorPalette;
  }

  int getFontSize() {
    if (myFontSize <= 0) {
      myFontSize = detectFontSize();
    }
    return myFontSize;
  }

  private int detectFontSize() {
    if (UISettings.getInstance().getPresentationMode()) {
      return UISettings.getInstance().getPresentationModeFontSize();
    }
    return myColorsScheme.getConsoleFontSize();
  }

  void setFontSize(int fontSize) {
    int prevFontSize = myFontSize;
    myFontSize = fontSize;
    if (prevFontSize != fontSize) {
      fireFontChanged();
    }
  }

  public void resetFontSize() {
    setFontSize(detectFontSize());
  }

  @Override
  public void dispose() {}
}
