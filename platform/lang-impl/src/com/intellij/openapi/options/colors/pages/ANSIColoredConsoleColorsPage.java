// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.colors.pages;

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import com.intellij.terminal.BlockTerminalColors;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.ui.EditorCustomization;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author oleg, Roman.Chernyatchik
 */
public final class ANSIColoredConsoleColorsPage implements ColorSettingsPage, DisplayPrioritySortable, EditorCustomization {
  @SuppressWarnings("SpellCheckingInspection")
  private static final String DEMO_TEXT =
    """
      <stdsys>C:\\command.com</stdsys>
      -<stdout> C:></stdout>
      -<stdin> help</stdin>
      <stderr>Bad command or file name</stderr>

      <logError>Log error</logError>
      <logWarning>Log warning</logWarning>
      <logInfo>Log info</logInfo>
      <logVerbose>Log verbose</logVerbose>
      <logDebug>Log debug</logDebug>
      <logExpired>An expired log entry</logExpired>

      # Process output highlighted using ANSI colors codes
      <black>ANSI: black</black>
      <red>ANSI: red</red>
      <green>ANSI: green</green>
      <yellow>ANSI: yellow</yellow>
      <blue>ANSI: blue</blue>
      <magenta>ANSI: magenta</magenta>
      <cyan>ANSI: cyan</cyan>
      <gray>ANSI: gray</gray>
      <darkGray>ANSI: dark gray</darkGray>
      <redBright>ANSI: bright red</redBright>
      <greenBright>ANSI: bright green</greenBright>
      <yellowBright>ANSI: bright yellow</yellowBright>
      <blueBright>ANSI: bright blue</blueBright>
      <magentaBright>ANSI: bright magenta</magentaBright>
      <cyanBright>ANSI: bright cyan</cyanBright>
      <white>ANSI: white</white>

      <terminalCommandToRunUsingIDE>git log</terminalCommandToRunUsingIDE>
      <stdsys>Process finished with exit code 1</stdsys>
      """;

  private static AttributesDescriptor[] ATTRS = {
    new AttributesDescriptor(OptionsBundle.messagePointer("options.general.color.descriptor.console.stdout"), ConsoleViewContentType.NORMAL_OUTPUT_KEY),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.general.color.descriptor.console.stderr"), ConsoleViewContentType.ERROR_OUTPUT_KEY),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.general.color.descriptor.console.stdin"), ConsoleViewContentType.USER_INPUT_KEY),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.general.color.descriptor.console.system.output"), ConsoleViewContentType.SYSTEM_OUTPUT_KEY),

    new AttributesDescriptor(OptionsBundle.messagePointer("options.general.color.descriptor.console.logError"), ConsoleViewContentType.LOG_ERROR_OUTPUT_KEY),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.general.color.descriptor.console.warning"), ConsoleViewContentType.LOG_WARNING_OUTPUT_KEY),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.general.color.descriptor.console.info"), ConsoleViewContentType.LOG_INFO_OUTPUT_KEY),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.general.color.descriptor.console.verbose"), ConsoleViewContentType.LOG_VERBOSE_OUTPUT_KEY),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.general.color.descriptor.console.debug"), ConsoleViewContentType.LOG_DEBUG_OUTPUT_KEY),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.general.color.descriptor.console.expired"), ConsoleViewContentType.LOG_EXPIRED_ENTRY),

    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.console.black"), ConsoleHighlighter.BLACK),
    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.console.red"), ConsoleHighlighter.RED),
    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.console.green"), ConsoleHighlighter.GREEN),
    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.console.yellow"), ConsoleHighlighter.YELLOW),
    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.console.blue"), ConsoleHighlighter.BLUE),
    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.console.magenta"), ConsoleHighlighter.MAGENTA),
    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.console.cyan"), ConsoleHighlighter.CYAN),
    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.console.white"), ConsoleHighlighter.GRAY),

    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.console.blackBright"), ConsoleHighlighter.DARKGRAY),
    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.console.redBright"), ConsoleHighlighter.RED_BRIGHT),
    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.console.greenBright"), ConsoleHighlighter.GREEN_BRIGHT),
    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.console.yellowBright"), ConsoleHighlighter.YELLOW_BRIGHT),
    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.console.blueBright"), ConsoleHighlighter.BLUE_BRIGHT),
    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.console.magentaBright"), ConsoleHighlighter.MAGENTA_BRIGHT),
    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.console.cyanBright"), ConsoleHighlighter.CYAN_BRIGHT),
    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.console.whiteBright"), ConsoleHighlighter.WHITE),

    new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.terminal.command.to.run.using.ide"),
                             JBTerminalSystemSettingsProviderBase.COMMAND_TO_RUN_USING_IDE_KEY),
  };

  private static ColorDescriptor[] COLORS = {
    new ColorDescriptor(OptionsBundle.messagePointer("options.general.color.descriptor.console.background"),
                        ConsoleViewContentType.CONSOLE_BACKGROUND_KEY, ColorDescriptor.Kind.BACKGROUND),
  };

  private static final Map<String, TextAttributesKey> ADDITIONAL_HIGHLIGHT_DESCRIPTORS = new HashMap<>();
  static {
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("stdsys", ConsoleViewContentType.SYSTEM_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("stdout", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("stdin", ConsoleViewContentType.USER_INPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("stderr", ConsoleViewContentType.ERROR_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("logError", ConsoleViewContentType.LOG_ERROR_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("logWarning", ConsoleViewContentType.LOG_WARNING_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("logInfo", ConsoleViewContentType.LOG_INFO_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("logVerbose", ConsoleViewContentType.LOG_VERBOSE_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("logDebug", ConsoleViewContentType.LOG_DEBUG_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("logExpired", ConsoleViewContentType.LOG_EXPIRED_ENTRY);

    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("black", ConsoleHighlighter.BLACK);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("red", ConsoleHighlighter.RED);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("green", ConsoleHighlighter.GREEN);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("yellow", ConsoleHighlighter.YELLOW);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("blue", ConsoleHighlighter.BLUE);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("magenta", ConsoleHighlighter.MAGENTA);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("cyan", ConsoleHighlighter.CYAN);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("gray", ConsoleHighlighter.GRAY);

    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("darkGray", ConsoleHighlighter.DARKGRAY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("redBright", ConsoleHighlighter.RED_BRIGHT);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("greenBright", ConsoleHighlighter.GREEN_BRIGHT);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("yellowBright", ConsoleHighlighter.YELLOW_BRIGHT);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("blueBright", ConsoleHighlighter.BLUE_BRIGHT);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("magentaBright", ConsoleHighlighter.MAGENTA_BRIGHT);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("cyanBright", ConsoleHighlighter.CYAN_BRIGHT);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("white", ConsoleHighlighter.WHITE);

    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("terminalCommandToRunUsingIDE", JBTerminalSystemSettingsProviderBase.COMMAND_TO_RUN_USING_IDE_KEY);

    if (ExperimentalUI.isNewUI() && Registry.is("ide.experimental.ui.new.terminal", false)) {
      AttributesDescriptor[] terminalAttrs = getBlockTerminalAttributes();
      ATTRS = ArrayUtil.mergeArrays(ATTRS, terminalAttrs);
      ColorDescriptor[] terminalColors = getBlockTerminalColors();
      COLORS = ArrayUtil.mergeArrays(COLORS, terminalColors);
    }
  }

  private static AttributesDescriptor[] getBlockTerminalAttributes() {
    return new AttributesDescriptor[]{
      new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.black"),
                               BlockTerminalColors.getBLACK()),
      new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.red"),
                               BlockTerminalColors.getRED()),
      new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.green"),
                               BlockTerminalColors.getGREEN()),
      new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.yellow"),
                               BlockTerminalColors.getYELLOW()),
      new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.blue"),
                               BlockTerminalColors.getBLUE()),
      new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.magenta"),
                               BlockTerminalColors.getMAGENTA()),
      new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.cyan"),
                               BlockTerminalColors.getCYAN()),
      new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.white"),
                               BlockTerminalColors.getWHITE()),

      new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.blackBright"),
                               BlockTerminalColors.getBLACK_BRIGHT()),
      new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.redBright"),
                               BlockTerminalColors.getRED_BRIGHT()),
      new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.greenBright"),
                               BlockTerminalColors.getGREEN_BRIGHT()),
      new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.yellowBright"),
                               BlockTerminalColors.getYELLOW_BRIGHT()),
      new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.blueBright"),
                               BlockTerminalColors.getBLUE_BRIGHT()),
      new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.magentaBright"),
                               BlockTerminalColors.getMAGENTA_BRIGHT()),
      new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.cyanBright"),
                               BlockTerminalColors.getCYAN_BRIGHT()),
      new AttributesDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.whiteBright"),
                               BlockTerminalColors.getWHITE_BRIGHT()),
    };
  }

  private static ColorDescriptor[] getBlockTerminalColors() {
    return new ColorDescriptor[]{
      new ColorDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.defaultForeground"),
                          BlockTerminalColors.getDEFAULT_FOREGROUND(), ColorDescriptor.Kind.FOREGROUND),
      new ColorDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.defaultBackground"),
                          BlockTerminalColors.getDEFAULT_BACKGROUND(), ColorDescriptor.Kind.BACKGROUND),
      new ColorDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.blockBackground.start"),
                          BlockTerminalColors.getBLOCK_BACKGROUND_START(), ColorDescriptor.Kind.BACKGROUND),
      new ColorDescriptor(OptionsBundle.messagePointer("color.settings.blockTerminal.blockBackground.end"),
                          BlockTerminalColors.getBLOCK_BACKGROUND_END(), ColorDescriptor.Kind.BACKGROUND),
    };
  }

  @Override
  public @NotNull Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ADDITIONAL_HIGHLIGHT_DESCRIPTORS;
  }

  @Override
  public @NotNull String getDisplayName() {
    return OptionsBundle.message("color.settings.console.name");
  }

  @Override
  public @NotNull Icon getIcon() {
    return PlainTextFileType.INSTANCE.getIcon();
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return ATTRS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return COLORS;
  }

  @Override
  public @NotNull SyntaxHighlighter getHighlighter() {
     return new PlainSyntaxHighlighter();
  }

  @Override
  public @NotNull String getDemoText() {
    return DEMO_TEXT;
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.COMMON_SETTINGS;
  }

  @Override
  public @NotNull EditorColorsScheme customizeColorScheme(@NotNull EditorColorsScheme scheme) {
    return ConsoleViewUtil.updateConsoleColorScheme(scheme);
  }

  @Override
  public void customize(@NotNull EditorEx editor) {
    editor.getSettings().setCaretRowShown(false);
  }
}
