// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.impl.tooltips.TooltipActionProvider;
import com.intellij.codeInsight.editorActions.SmartBackspaceMode;
import com.intellij.ide.ui.UISettings;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.beans.MetricEventUtilKt;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.editor.actions.CaretStopBoundary;
import com.intellij.openapi.editor.actions.CaretStopOptions;
import com.intellij.openapi.editor.actions.CaretStopOptionsTransposed;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.editor.richcopy.settings.RichCopySettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.tabs.FileColorManagerImpl;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

final class EditorSettingsStatisticsCollector extends ApplicationUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("editor.settings.ide", 12);
  private static final EnumEventField<Settings> SETTING_ID = EventFields.Enum("setting_id", Settings.class, it -> it.internalName);
  private static final IntEventField INT_VALUE_FIELD = EventFields.Int("value");
  private static final StringEventField TRAILING_SPACES_FIELD = EventFields.String("value", List.of("Whole", "Changed", "None"));
  private static final EnumEventField<SmartBackspaceMode> BACKSPACE_MODE = EventFields.Enum("value", SmartBackspaceMode.class);
  private static final EnumEventField<CaretStopBoundaries> CARET_STOP_BOUNDARIES = EventFields.Enum("value", CaretStopBoundaries.class);
  private static final VarargEventId SETTING = GROUP.registerVarargEvent("not.default",
                                                                         EventFields.Enabled,
                                                                         SETTING_ID,
                                                                         EventFields.LanguageById,
                                                                         INT_VALUE_FIELD,
                                                                         TRAILING_SPACES_FIELD,
                                                                         BACKSPACE_MODE,
                                                                         CARET_STOP_BOUNDARIES);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  public @NotNull Set<MetricEvent> getMetrics() {
    Set<MetricEvent> set = new HashSet<>();

    EditorSettingsExternalizable es = EditorSettingsExternalizable.getInstance();
    EditorSettingsExternalizable esDefault = new EditorSettingsExternalizable(new EditorSettingsExternalizable.OsSpecificState());
    addBoolIfDiffers(set, es, esDefault, s -> s.isVirtualSpace(), Settings.CARET_AFTER_LINE_END);
    addBoolIfDiffers(set, es, esDefault, s -> s.isCaretInsideTabs(), Settings.CARET_INSIDE_TABS);
    addBoolIfDiffers(set, es, esDefault, s -> s.isAdditionalPageAtBottom(), Settings.VIRTUAL_SPACE_AT_FILE_BOTTOM);
    addBoolIfDiffers(set, es, esDefault, s -> s.isUseSoftWraps(SoftWrapAppliancePlaces.MAIN_EDITOR), Settings.SOFT_WRAPS);
    addBoolIfDiffers(set, es, esDefault, s -> s.isUseSoftWraps(SoftWrapAppliancePlaces.CONSOLE), Settings.SOFT_WRAPS_CONSOLE);
    addBoolIfDiffers(set, es, esDefault, s -> s.isUseSoftWraps(SoftWrapAppliancePlaces.PREVIEW), Settings.SOFT_WRAPS_PREVIEW);
    addBoolIfDiffers(set, es, esDefault, s -> s.isUseCustomSoftWrapIndent(), Settings.SOFT_WRAPS_RELATIVE_INDENT);
    addBoolIfDiffers(set, es, esDefault, s -> s.isAllSoftWrapsShown(), Settings.SOFT_WRAPS_SHOW_ALL);
    addIfDiffers(set, es, esDefault, s -> s.getStripTrailingSpaces(), Settings.STRIP_TRAILING_SPACES, TRAILING_SPACES_FIELD);
    addBoolIfDiffers(set, es, esDefault, s -> s.isEnsureNewLineAtEOF(), Settings.ENSURE_NEWLINE_AT_EOF);
    addBoolIfDiffers(set, es, esDefault, s -> s.isShowQuickDocOnMouseOverElement(), Settings.QUICK_DOC_ON_MOUSE_HOVER);
    addBoolIfDiffers(set, es, esDefault, s -> s.isBlinkCaret(), Settings.BLINKING_CARET);
    addIfDiffers(set, es, esDefault, s -> s.getBlinkPeriod(), Settings.BLINK_PERIOD, INT_VALUE_FIELD);
    addBoolIfDiffers(set, es, esDefault, s -> s.isBlockCursor(), Settings.BLOCK_CARET);
    addBoolIfDiffers(set, es, esDefault, s -> s.isHighlightSelectionOccurrences(), Settings.SELECTION_OCCURRENCES_HIGHLIGHT);
    addBoolIfDiffers(set, es, esDefault, s -> s.isRightMarginShown(), Settings.RIGHT_MARGIN);
    addBoolIfDiffers(set, es, esDefault, s -> s.isLineNumbersShown(), Settings.LINE_NUMBERS);
    addBoolIfDiffers(set, es, esDefault, s -> s.areGutterIconsShown(), Settings.GUTTER_ICONS);
    addBoolIfDiffers(set, es, esDefault, s -> s.isFoldingOutlineShown(), Settings.FOLDING_OUTLINE);
    addBoolIfDiffers(set, es, esDefault, s -> s.isFoldingOutlineShownOnlyOnHover(), Settings.FOLDING_OUTLINE_ONLY_ON_HOVER);
    addBoolIfDiffers(set, es, esDefault, s -> s.isWhitespacesShown() && s.isLeadingWhitespacesShown(), Settings.SHOW_LEADING_WHITESPACE);
    addBoolIfDiffers(set, es, esDefault, s -> s.isWhitespacesShown() && s.isInnerWhitespacesShown(), Settings.SHOW_INNER_WHITESPACE);
    addBoolIfDiffers(set, es, esDefault, s -> s.isWhitespacesShown() && s.isTrailingWhitespacesShown(), Settings.SHOW_TRAILING_WHITESPACE);
    addBoolIfDiffers(set, es, esDefault, s -> s.isIndentGuidesShown(), Settings.INDENT_GUIDES);
    addBoolIfDiffers(set, es, esDefault, s -> s.isSmoothScrolling(), Settings.ANIMATED_SCROLL);
    addBoolIfDiffers(set, es, esDefault, s -> s.isDndEnabled(), Settings.DRAG_N_DROP);
    addBoolIfDiffers(set, es, esDefault, s -> s.isWheelFontChangeEnabled(), Settings.WHEEL_ZOOM);
    addBoolIfDiffers(set, es, esDefault, s -> s.isMouseClickSelectionHonorsCamelWords(), Settings.MOUSE_CAMEL);
    addBoolIfDiffers(set, es, esDefault, s -> s.isVariableInplaceRenameEnabled(), Settings.INPLACE_RENAME);
    addBoolIfDiffers(set, es, esDefault, s -> s.isPreselectRename(), Settings.PRESELECT_ON_RENAME);
    addBoolIfDiffers(set, es, esDefault, s -> s.isShowInlineLocalDialog(), Settings.INLINE_DIALOG);
    addBoolIfDiffers(set, es, esDefault, s -> s.isRefrainFromScrolling(), Settings.MINIMIZE_SCROLLING);
    addBoolIfDiffers(set, es, esDefault, s -> s.isShowNotificationAfterReformat(), Settings.AFTER_REFORMAT_NOTIFICATION);
    addBoolIfDiffers(set, es, esDefault, s -> s.isShowNotificationAfterOptimizeImports(), Settings.AFTER_OPTIMIZE_NOTIFICATION);
    addBoolIfDiffers(set, es, esDefault, s -> s.isSmartHome(), Settings.SMART_HOME);
    addBoolIfDiffers(set, es, esDefault, s -> s.isCamelWords(), Settings.CAMEL_WORDS);
    addBoolIfDiffers(set, es, esDefault, s -> s.isBreadcrumbsAbove(), Settings.BREADCRUMBS_ABOVE);
    addBoolIfDiffers(set, es, esDefault, s -> s.isBreadcrumbsShown(), Settings.ALL_BREADCRUMBS);
    addBoolIfDiffers(set, es, esDefault, s -> s.areStickyLinesShown(), Settings.STICKY_LINES);
    addBoolIfDiffers(set, es, esDefault, s -> s.isShowIntentionBulb(), Settings.INTENTION_BULB);
    addBoolIfDiffers(set, es, esDefault, s -> s.isDocCommentRenderingEnabled(), Settings.RENDER_DOC);
    addBoolIfDiffers(set, es, esDefault, s -> s.isShowIntentionPreview(), Settings.INTENTION_PREVIEW);
    addBoolIfDiffers(set, es, esDefault, s -> s.isUseEditorFontInInlays(), Settings.USE_EDITOR_FONT_IN_INLAYS);

    for (String language : es.getOptions().getLanguageBreadcrumbsMap().keySet()) {
      addBoolIfDiffers(set, es, esDefault, s -> s.isBreadcrumbsShownFor(language), Settings.BREADCRUMBS,
                       EventFields.LanguageById.with(language));
    }

    for (String language : es.getOptions().getLanguageStickyLines().keySet()) {
      addBoolIfDiffers(set, es, esDefault, s -> s.areStickyLinesShownFor(language), Settings.STICKY_LINES_FOR_LANG,
                       EventFields.LanguageById.with(language));
    }

    RichCopySettings rcs = RichCopySettings.getInstance();
    RichCopySettings rcsDefault = new RichCopySettings();
    addBoolIfDiffers(set, rcs, rcsDefault, s -> s.isEnabled(), Settings.RICH_COPY);

    CodeInsightSettings cis = CodeInsightSettings.getInstance();
    CodeInsightSettings cisDefault = new CodeInsightSettings();
    addBoolIfDiffers(set, cis, cisDefault, s -> s.AUTO_POPUP_PARAMETER_INFO, Settings.PARAMETER_AUTO_POPUP);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.AUTO_POPUP_JAVADOC_INFO, Settings.JAVADOC_AUTO_POPUP);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.AUTO_POPUP_COMPLETION_LOOKUP, Settings.COMPLETION_AUTO_POPUP);
    addIfDiffers(set, cis, cisDefault, s -> s.COMPLETION_CASE_SENSITIVE, Settings.COMPLETION_CASE_SENSITIVITY, INT_VALUE_FIELD);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.isSelectAutopopupSuggestionsByChars(), Settings.AUTO_POPUP_CHAR_COMPLETE);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.AUTOCOMPLETE_ON_CODE_COMPLETION, Settings.AUTO_COMPLETE_BASIC);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION, Settings.AUTO_COMPLETE_SMART);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO, Settings.PARAMETER_INFO_FULL_SIGNATURE);
    addIfDiffers(set, cis, cisDefault, s -> s.getBackspaceMode(), Settings.SMART_BACKSPACE, BACKSPACE_MODE);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.SMART_INDENT_ON_ENTER, Settings.INDENT_ON_ENTER);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.INSERT_BRACE_ON_ENTER, Settings.BRACE_ON_ENTER);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.JAVADOC_STUB_ON_ENTER, Settings.JAVADOC_ON_ENTER);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.INSERT_SCRIPTLET_END_ON_ENTER, Settings.SCRIPTLET_END_ON_ENTER);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.SMART_END_ACTION, Settings.SMART_END);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.JAVADOC_GENERATE_CLOSING_TAG, Settings.AUTO_CLOSE_JAVADOC_TAGS);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.SURROUND_SELECTION_ON_QUOTE_TYPED, Settings.SURROUND_BY_QUOTE_OR_BRACE);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.AUTOINSERT_PAIR_BRACKET, Settings.PAIR_BRACKET_AUTO_INSERT);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.AUTOINSERT_PAIR_QUOTE, Settings.PAIR_QUOTE_AUTO_INSERT);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.REFORMAT_BLOCK_ON_RBRACE, Settings.REFORMAT_ON_R_BRACE);
    addIfDiffers(set, cis, cisDefault, s -> s.REFORMAT_ON_PASTE, Settings.REFORMAT_ON_PASTE, INT_VALUE_FIELD);
    addIfDiffers(set, cis, cisDefault, s -> s.ADD_IMPORTS_ON_PASTE, Settings.IMPORTS_ON_PASTE, INT_VALUE_FIELD);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.HIGHLIGHT_BRACES, Settings.BRACES_HIGHLIGHT);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.HIGHLIGHT_SCOPE, Settings.SCOPE_HIGHLIGHT);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.HIGHLIGHT_IDENTIFIER_UNDER_CARET, Settings.IDENTIFIER_UNDER_CARET_HIGHLIGHT);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY, Settings.AUTO_ADD_IMPORTS);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION, Settings.COMPLETION_HINTS);
    addBoolIfDiffers(set, cis, cisDefault, s -> s.TAB_EXITS_BRACKETS_AND_QUOTES, Settings.TAB_EXITS_BRACKETS_AND_QUOTES);
    addTooltipActionsMetricIfDiffers(set);

    DaemonCodeAnalyzerSettings dcas = DaemonCodeAnalyzerSettings.getInstance();
    DaemonCodeAnalyzerSettings dcasDefault = new DaemonCodeAnalyzerSettings();
    addBoolIfDiffers(set, dcas, dcasDefault, s -> s.isNextErrorActionGoesToErrorsFirst(), Settings.NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST);
    addIfDiffers(set, dcas, dcasDefault, s -> s.getAutoReparseDelay(), Settings.AUTO_REPARSE_DELAY, INT_VALUE_FIELD);
    addIfDiffers(set, dcas, dcasDefault, s -> s.getErrorStripeMarkMinHeight(), Settings.ERROR_STRIPE_MARK_MIN_HEIGHT, INT_VALUE_FIELD);
    addBoolIfDiffers(set, dcas, dcasDefault, s -> s.isSuppressWarnings(), Settings.SUPPRESS_WARNINGS);
    addBoolIfDiffers(set, dcas, dcasDefault, s -> s.isImportHintEnabled(), Settings.IMPORT_HINT_ENABLED);
    addBoolIfDiffers(set, dcas, dcasDefault, s -> s.SHOW_METHOD_SEPARATORS, Settings.SHOW_METHOD_SEPARATORS);

    final CaretStopOptionsTransposed defaultCaretStop = CaretStopOptionsTransposed.fromCaretStopOptions(new CaretStopOptions());
    final CaretStopOptionsTransposed caretStop = CaretStopOptionsTransposed.fromCaretStopOptions(es.getCaretStopOptions());
    addIfDiffers(set, caretStop.getLineBoundary(), defaultCaretStop.getLineBoundary(), s -> toCaretStopValue(s),
                 Settings.CARET_MOVEMENT_WORD, CARET_STOP_BOUNDARIES);
    addIfDiffers(set, caretStop.getWordBoundary(), defaultCaretStop.getWordBoundary(), s -> toCaretStopValue(s),
                 Settings.CARET_MOVEMENT_LINE, CARET_STOP_BOUNDARIES);

    if (!FileColorManagerImpl._isEnabled()) {
      set.add(SETTING.metric(SETTING_ID.with(Settings.FILE_COLORS_ENABLED), EventFields.Enabled.with(false)));
    }
    if (!FileColorManagerImpl._isEnabledForProjectView()) {
      set.add(SETTING.metric(SETTING_ID.with(Settings.FILE_COLORS_ENABLED_FOR_PROJECT_VIEW), EventFields.Enabled.with(false)));
    }
    if (!FileColorManagerImpl._isEnabledForTabs()) {
      set.add(SETTING.metric(SETTING_ID.with(Settings.FILE_COLORS_ENABLED_FOR_TABS), EventFields.Enabled.with(false)));
    }

    UISettings uiSettings = UISettings.getInstance();
    UISettings uiSettingsDefault = new UISettings();
    addBoolIfDiffers(set, uiSettings, uiSettingsDefault, s -> s.getOpenTabsInMainWindow(), Settings.OPEN_TABS_IN_MAIN_WINDOW);

    return set;
  }

  private static <T> void addBoolIfDiffers(@NotNull Set<? super MetricEvent> set,
                                           @NotNull T settingsBean,
                                           @NotNull T defaultSettingsBean,
                                           @NotNull Function<T, Boolean> valueFunction,
                                           @NotNull Settings setting,
                                           EventPair<?> @NotNull ... pairs) {
    Boolean value = valueFunction.apply(settingsBean);
    Boolean defaultValue = valueFunction.apply(defaultSettingsBean);
    if (!Comparing.equal(value, defaultValue)) {
      List<EventPair<?>> values = new ArrayList<>(Arrays.asList(pairs));
      values.add(SETTING_ID.with(setting));
      values.add(EventFields.Enabled.with(value));
      set.add(SETTING.metric(values));
    }
  }

  private static <T, V> void addIfDiffers(@NotNull Set<? super MetricEvent> set,
                                          @NotNull T settingsBean,
                                          @NotNull T defaultSettingsBean,
                                          @NotNull Function<? super T, ? extends V> valueFunction,
                                          @NotNull Settings setting,
                                          @NotNull EventField<V> field) {
    V value = valueFunction.apply(settingsBean);
    V defaultValue = valueFunction.apply(defaultSettingsBean);
    if (!Comparing.equal(value, defaultValue)) {
      set.add(SETTING.metric(SETTING_ID.with(setting), field.with(value)));
    }
  }

  private static CaretStopBoundaries toCaretStopValue(@NotNull CaretStopBoundary boundary) {
    if (boundary.equals(CaretStopBoundary.NONE)) {
      return CaretStopBoundaries.NONE;
    }
    else if (boundary.equals(CaretStopBoundary.CURRENT)) {
      return CaretStopBoundaries.CURRENT;
    }
    else if (boundary.equals(CaretStopBoundary.NEIGHBOR)) {
      return CaretStopBoundaries.NEIGHBOR;
    }
    else if (boundary.equals(CaretStopBoundary.START)) {
      return CaretStopBoundaries.START;
    }
    else if (boundary.equals(CaretStopBoundary.END)) {
      return CaretStopBoundaries.END;
    }
    else if (boundary.equals(CaretStopBoundary.BOTH)) return CaretStopBoundaries.BOTH;
    return CaretStopBoundaries.OTHER;
  }

  private static void addTooltipActionsMetricIfDiffers(@NotNull Set<? super MetricEvent> set) {
    boolean value = TooltipActionProvider.isShowActions();
    if (value != TooltipActionProvider.SHOW_FIXES_DEFAULT_VALUE) {
      set.add(SETTING.metric(SETTING_ID.with(Settings.SHOW_ACTIONS_IN_TOOLTIP), EventFields.Enabled.with(value)));
    }
  }

  private enum CaretStopBoundaries {
    NONE,
    CURRENT,
    NEIGHBOR,
    START,
    END,
    BOTH,
    OTHER
  }

  private enum Settings {
    CARET_AFTER_LINE_END("caretAfterLineEnd"),
    CARET_INSIDE_TABS("caretInsideTabs"),
    VIRTUAL_SPACE_AT_FILE_BOTTOM("virtualSpaceAtFileBottom"),
    SOFT_WRAPS("softWraps"),
    SOFT_WRAPS_CONSOLE("softWraps.console"),
    SOFT_WRAPS_PREVIEW("softWraps.preview"),
    SOFT_WRAPS_RELATIVE_INDENT("softWraps.relativeIndent"),
    SOFT_WRAPS_SHOW_ALL("softWraps.showAll"),
    ENSURE_NEWLINE_AT_EOF("ensureNewlineAtEOF"),
    QUICK_DOC_ON_MOUSE_HOVER("quickDocOnMouseHover"),
    BLINKING_CARET("blinkingCaret"),
    BLOCK_CARET("blockCaret"),
    SELECTION_OCCURRENCES_HIGHLIGHT("selectionOccurrencesHighlight"),
    RIGHT_MARGIN("rightMargin"),
    LINE_NUMBERS("lineNumbers"),
    GUTTER_ICONS("gutterIcons"),
    FOLDING_OUTLINE("foldingOutline"),
    FOLDING_OUTLINE_ONLY_ON_HOVER("foldingOutlineOnlyOnHover"),
    SHOW_LEADING_WHITESPACE("showLeadingWhitespace"),
    SHOW_INNER_WHITESPACE("showInnerWhitespace"),
    SHOW_TRAILING_WHITESPACE("showTrailingWhitespace"),
    INDENT_GUIDES("indentGuides"),
    ANIMATED_SCROLL("animatedScroll"),
    DRAG_N_DROP("dragNDrop"),
    WHEEL_ZOOM("wheelZoom"),
    MOUSE_CAMEL("mouseCamel"),
    INPLACE_RENAME("inplaceRename"),
    PRESELECT_ON_RENAME("preselectOnRename"),
    INLINE_DIALOG("inlineDialog"),
    MINIMIZE_SCROLLING("minimizeScrolling"),
    AFTER_REFORMAT_NOTIFICATION("afterReformatNotification"),
    AFTER_OPTIMIZE_NOTIFICATION("afterOptimizeNotification"),
    SMART_HOME("smartHome"),
    CAMEL_WORDS("camelWords"),
    BREADCRUMBS_ABOVE("breadcrumbsAbove"),
    ALL_BREADCRUMBS("all.breadcrumbs"),
    INTENTION_BULB("intentionBulb"),
    RENDER_DOC("renderDoc"),
    INTENTION_PREVIEW("intentionPreview"),
    USE_EDITOR_FONT_IN_INLAYS("useEditorFontInInlays"),
    BREADCRUMBS("breadcrumbs"),
    STICKY_LINES("stickyLines"),
    STICKY_LINES_FOR_LANG("stickyLinesForLang"),
    RICH_COPY("richCopy"),
    PARAMETER_AUTO_POPUP("parameterAutoPopup"),
    JAVADOC_AUTO_POPUP("javadocAutoPopup"),
    COMPLETION_AUTO_POPUP("completionAutoPopup"),
    AUTO_POPUP_CHAR_COMPLETE("autoPopupCharComplete"),
    AUTO_COMPLETE_BASIC("autoCompleteBasic"),
    AUTO_COMPLETE_SMART("autoCompleteSmart"),
    PARAMETER_INFO_FULL_SIGNATURE("parameterInfoFullSignature"),
    INDENT_ON_ENTER("indentOnEnter"),
    BRACE_ON_ENTER("braceOnEnter"),
    JAVADOC_ON_ENTER("javadocOnEnter"),
    SCRIPTLET_END_ON_ENTER("scriptletEndOnEnter"),
    SMART_END("smartEnd"),
    AUTO_CLOSE_JAVADOC_TAGS("autoCloseJavadocTags"),
    SURROUND_BY_QUOTE_OR_BRACE("surroundByQuoteOrBrace"),
    PAIR_BRACKET_AUTO_INSERT("pairBracketAutoInsert"),
    PAIR_QUOTE_AUTO_INSERT("pairQuoteAutoInsert"),
    REFORMAT_ON_R_BRACE("reformatOnRBrace"),
    BRACES_HIGHLIGHT("bracesHighlight"),
    SCOPE_HIGHLIGHT("scopeHighlight"),
    IDENTIFIER_UNDER_CARET_HIGHLIGHT("identifierUnderCaretHighlight"),
    AUTO_ADD_IMPORTS("autoAddImports"),
    COMPLETION_HINTS("completionHints"),
    TAB_EXITS_BRACKETS_AND_QUOTES("tabExitsBracketsAndQuotes"),
    NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST("nextErrorActionGoesToErrorsFirst"),
    SUPPRESS_WARNINGS("suppressWarnings"),
    IMPORT_HINT_ENABLED("importHintEnabled"),
    SHOW_METHOD_SEPARATORS("showMethodSeparators"),
    OPEN_TABS_IN_MAIN_WINDOW("openTabsInMainWindow"),

    STRIP_TRAILING_SPACES("stripTrailingSpaces"),
    BLINK_PERIOD("blinkPeriod"),
    COMPLETION_CASE_SENSITIVITY("completionCaseSensitivity"),
    SMART_BACKSPACE("smartBackspace"),
    REFORMAT_ON_PASTE("reformatOnPaste"),
    IMPORTS_ON_PASTE("importsOnPaste"),
    AUTO_REPARSE_DELAY("autoReparseDelay"),
    ERROR_STRIPE_MARK_MIN_HEIGHT("errorStripeMarkMinHeight"),
    CARET_MOVEMENT_WORD("caret.movement.word"),
    CARET_MOVEMENT_LINE("caret.movement.line"),

    FILE_COLORS_ENABLED("fileColorsEnabled"),
    FILE_COLORS_ENABLED_FOR_PROJECT_VIEW("fileColorsEnabledForProjectView"),
    FILE_COLORS_ENABLED_FOR_TABS("fileColorsEnabledForTabs"),
    SHOW_ACTIONS_IN_TOOLTIP("show.actions.in.tooltip"),
    ;

    public final String internalName;

    Settings(String internalName) { this.internalName = internalName; }
  }

  public static final class ProjectUsages extends ProjectUsagesCollector {
    private static final EventLogGroup GROUP = new EventLogGroup("editor.settings.project", 3);
    private static final VarargEventId AUTO_OPTIMIZE_IMPORTS = GROUP.registerVarargEvent("autoOptimizeImports", EventFields.Enabled);

    @Override
    public EventLogGroup getGroup() {
      return GROUP;
    }

    @Override
    public @NotNull Set<MetricEvent> getMetrics(@NotNull Project project) {
      Set<MetricEvent> set = new HashSet<>();
      CodeInsightWorkspaceSettings ciws = CodeInsightWorkspaceSettings.getInstance(project);
      CodeInsightWorkspaceSettings ciwsDefault = new CodeInsightWorkspaceSettings();
      MetricEventUtilKt.addBoolIfDiffers(set, ciws, ciwsDefault, s -> s.isOptimizeImportsOnTheFly(), AUTO_OPTIMIZE_IMPORTS);
      return set;
    }
  }
}
