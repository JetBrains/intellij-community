// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ShowContentAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.ui.TerminalAction;
import com.jediterm.terminal.ui.TerminalActionPresentation;
import com.jediterm.terminal.ui.settings.DefaultTabbedSettingsProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JBTerminalSystemSettingsProviderBase extends DefaultTabbedSettingsProvider {

  public static final TextAttributesKey COMMAND_TO_RUN_USING_IDE_KEY =
    TextAttributesKey.createTextAttributesKey("TERMINAL_COMMAND_TO_RUN_USING_IDE");

  private final TerminalUiSettingsManager myUiSettingsManager;

  public JBTerminalSystemSettingsProviderBase() {
    myUiSettingsManager = TerminalUiSettingsManager.getInstance();
  }

  @NotNull TerminalUiSettingsManager getUiSettingsManager() {
    return myUiSettingsManager;
  }

  @NotNull EditorColorsScheme getColorsScheme() {
    return myUiSettingsManager.getEditorColorsScheme();
  }

  @Override
  public @NotNull TerminalActionPresentation getNewSessionActionPresentation() {
    TerminalActionPresentation presentation = super.getNewSessionActionPresentation();
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.NewSession.text"),
                                          presentation.getKeyStrokes());
  }

  @Override
  public @NotNull TerminalActionPresentation getOpenUrlActionPresentation() {
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.OpenAsUrl.text"), Collections.emptyList());
  }

  @Override
  public @NotNull TerminalActionPresentation getCopyActionPresentation() {
    List<KeyStroke> strokes = getKeyStrokesByActionId("Terminal.CopySelectedText");
    if (strokes.isEmpty()) {
      strokes = getKeyStrokesByActionId(IdeActions.ACTION_COPY);
    }
    return new TerminalActionPresentation(UIUtil.removeMnemonic(ActionsBundle.message("action.$Copy.text")), strokes);
  }

  @Override
  public @NotNull TerminalActionPresentation getPasteActionPresentation() {
    List<KeyStroke> strokes = getKeyStrokesByActionId("Terminal.Paste");
    if (strokes.isEmpty()) {
      strokes = getKeyStrokesByActionId(IdeActions.ACTION_PASTE);
    }
    return new TerminalActionPresentation(UIUtil.removeMnemonic(ActionsBundle.message("action.$Paste.text")), strokes);
  }

  @Override
  public @NotNull TerminalActionPresentation getSelectAllActionPresentation() {
    List<KeyStroke> strokes = getKeyStrokesByActionId("Terminal.SelectAll");
    return new TerminalActionPresentation(UIUtil.removeMnemonic(ActionsBundle.message("action.$SelectAll.text")), strokes);
  }

  @Override
  public @NotNull TerminalActionPresentation getClearBufferActionPresentation() {
    List<KeyStroke> strokes = getKeyStrokesByActionId("Terminal.ClearBuffer");
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.ClearBuffer.text"), strokes);
  }

  @Override
  public @NotNull TerminalActionPresentation getPageUpActionPresentation() {
    TerminalActionPresentation presentation = super.getPageUpActionPresentation();
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.PageUp.text"),
                                          presentation.getKeyStrokes());
  }

  @Override
  public @NotNull TerminalActionPresentation getPageDownActionPresentation() {
    TerminalActionPresentation presentation = super.getPageDownActionPresentation();
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.PageDown.text"),
                                          presentation.getKeyStrokes());
  }

  @Override
  public @NotNull TerminalActionPresentation getLineUpActionPresentation() {
    TerminalActionPresentation presentation = super.getLineUpActionPresentation();
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.LineUp.text"),
                                          presentation.getKeyStrokes());
  }

  @Override
  public @NotNull TerminalActionPresentation getLineDownActionPresentation() {
    TerminalActionPresentation presentation = super.getLineDownActionPresentation();
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.LineDown.text"),
                                          presentation.getKeyStrokes());
  }

  @Override
  public @NotNull TerminalActionPresentation getCloseSessionActionPresentation() {
    List<KeyStroke> keyStrokes = ContainerUtil.concat(super.getCloseSessionActionPresentation().getKeyStrokes(),
                                                      getKeyStrokesByActionId("CloseActiveTab"));
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.CloseSession.text"), keyStrokes);
  }

  @Override
  public @NotNull TerminalActionPresentation getFindActionPresentation() {
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.Find.text"),
                                          getKeyStrokesByActionId(IdeActions.ACTION_FIND));
  }

  @NotNull
  @Override
  public ColorPalette getTerminalColorPalette() {
    return myUiSettingsManager.getTerminalColorPalette();
  }

  public static @NotNull @Nls String getGotoNextSplitTerminalActionText(boolean forward) {
    return forward ? ActionsBundle.message("action.NextSplitter.text")
                   : ActionsBundle.message("action.PrevSplitter.text");
  }

  public @NotNull TerminalAction getGotoNextSplitTerminalAction(@Nullable JBTerminalWidgetListener listener, boolean forward) {
    String actionId = forward ? "Terminal.NextSplitter" : "Terminal.PrevSplitter";
    String text = UIUtil.removeMnemonic(getGotoNextSplitTerminalActionText(forward));
    return new TerminalAction(new TerminalActionPresentation(text, getKeyStrokesByActionId(actionId)), event -> {
      if (listener != null) {
        listener.gotoNextSplitTerminal(forward);
      }
      return true;
    });
  }

  public static @NotNull List<KeyStroke> getKeyStrokesByActionId(@NotNull String actionId) {
    List<KeyStroke> keyStrokes = new ArrayList<>();
    Shortcut[] shortcuts = KeymapUtil.getActiveKeymapShortcuts(actionId).getShortcuts();
    for (Shortcut sc : shortcuts) {
      if (sc instanceof KeyboardShortcut) {
        KeyStroke ks = ((KeyboardShortcut)sc).getFirstKeyStroke();
        keyStrokes.add(ks);
      }
    }
    return keyStrokes;
  }

  @Override
  public @NotNull TerminalActionPresentation getNextTabActionPresentation() {
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.SelectNextTab.text"), getKeyStrokesByActionId("NextTab"));
  }

  @Override
  public @NotNull TerminalActionPresentation getPreviousTabActionPresentation() {
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.SelectPreviousTab.text"),
                                          getKeyStrokesByActionId("PreviousTab"));
  }

  public @NotNull TerminalActionPresentation getMoveTabRightActionPresentation() {
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.MoveRight.text"),
                                          getKeyStrokesByActionId("Terminal.MoveToolWindowTabRight"));
  }

  public @NotNull TerminalActionPresentation getMoveTabLeftActionPresentation() {
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.MoveLeft.text"),
                                          getKeyStrokesByActionId("Terminal.MoveToolWindowTabLeft"));
  }

  public @NotNull TerminalActionPresentation getShowTabsActionPresentation() {
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.ShowTabs.text"),
                                          getKeyStrokesByActionId(ShowContentAction.ACTION_ID));
  }


  @Override
  public float getLineSpacing() {
    return getColorsScheme().getConsoleLineSpacing();
  }

  @Override
  public TextStyle getSelectionColor() {
    return new TextStyle(TerminalColor.awt(getColorsScheme().getColor(EditorColors.SELECTION_FOREGROUND_COLOR)),
                         TerminalColor.awt(getColorsScheme().getColor(EditorColors.SELECTION_BACKGROUND_COLOR)));
  }

  @Override
  public TextStyle getFoundPatternColor() {
    return new TextStyle(TerminalColor.awt(getColorsScheme().getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES).getForegroundColor()),
                         TerminalColor.awt(getColorsScheme().getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES).getBackgroundColor()));
  }

  @Override
  public TextStyle getHyperlinkColor() {
    return new TextStyle(TerminalColor.awt(getColorsScheme().getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR).getForegroundColor()),
                         TerminalColor.awt(getColorsScheme().getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR).getBackgroundColor()));
  }

  @Override
  public TextStyle getDefaultStyle() {
    return new TextStyle(new TerminalColor(() -> myUiSettingsManager.getDefaultForeground()),
                         new TerminalColor(() -> myUiSettingsManager.getDefaultBackground()));
  }

  @Override
  public Font getTerminalFont() {
    Font font = getColorsScheme().getFont(EditorFontType.CONSOLE_PLAIN);
    return font.deriveFont(getTerminalFontSize());
  }

  @Override
  public float getTerminalFontSize() {
    return (float)myUiSettingsManager.getFontSize();
  }

  @Override
  public boolean useAntialiasing() {
    return true; // we return true here because all the settings are checked again in UiSettings.setupAntialiasing
  }

  @Override
  public boolean copyOnSelect() {
    return SystemInfo.isLinux;
  }

  @Override
  public boolean pasteOnMiddleMouseClick() {
    return true;
  }

  @Override
  public int getBufferMaxLinesCount() {
    final int linesCount = AdvancedSettings.getInt("terminal.buffer.max.lines.count");
    if (linesCount > 0) {
      return linesCount;
    }
    else {
      return super.getBufferMaxLinesCount();
    }
  }

  public boolean overrideIdeShortcuts() {
    return false;
  }

  @Override
  public boolean useInverseSelectionColor() {
    return false;
  }

  @Override
  public int caretBlinkingMs() {
    EditorSettingsExternalizable instance = EditorSettingsExternalizable.getInstance();
    return instance.isBlinkCaret() ? instance.getBlinkPeriod() : 0;
  }

  public @NotNull CursorShape getCursorShape() {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    TerminalUiSettingsManager.CursorShape shape = TerminalUiSettingsManager.getInstance().getCursorShape();
    if (shape == TerminalUiSettingsManager.CursorShape.BLOCK) {
      return editorSettings.isBlinkCaret() ? CursorShape.BLINK_BLOCK : CursorShape.STEADY_BLOCK;
    }
    if (shape == TerminalUiSettingsManager.CursorShape.UNDERLINE) {
      return editorSettings.isBlinkCaret() ? CursorShape.BLINK_UNDERLINE : CursorShape.STEADY_UNDERLINE;
    }
    return editorSettings.isBlinkCaret() ? CursorShape.BLINK_VERTICAL_BAR : CursorShape.STEADY_VERTICAL_BAR;
  }
}
