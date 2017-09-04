/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.statistic.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.internal.statistic.AbstractProjectsUsagesCollector;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.editor.richcopy.settings.RichCopySettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.BooleanFunction;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

class EditorSettingsStatisticsCollector extends UsagesCollector {
  private static final GroupDescriptor GROUP_DESCRIPTOR = GroupDescriptor.create("Editor");

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GROUP_DESCRIPTOR;
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    Set<UsageDescriptor> set = new HashSet<>();
    
    EditorSettingsExternalizable es = EditorSettingsExternalizable.getInstance();
    EditorSettingsExternalizable esDefault = new EditorSettingsExternalizable();
    addBoolIfDiffers(set, es, esDefault, s -> s.isVirtualSpace(), "caretAfterLineEnd");
    addBoolIfDiffers(set, es, esDefault, s -> s.isCaretInsideTabs(), "caretInsideTabs");
    addBoolIfDiffers(set, es, esDefault, s -> s.isAdditionalPageAtBottom(), "virtualSpaceAtFileBottom");
    addBoolIfDiffers(set, es, esDefault, s -> s.isUseSoftWraps(SoftWrapAppliancePlaces.MAIN_EDITOR), "softWraps");
    addBoolIfDiffers(set, es, esDefault, s -> s.isUseSoftWraps(SoftWrapAppliancePlaces.CONSOLE), "softWraps.console");
    addBoolIfDiffers(set, es, esDefault, s -> s.isUseSoftWraps(SoftWrapAppliancePlaces.PREVIEW), "softWraps.preview");
    addBoolIfDiffers(set, es, esDefault, s -> s.isUseCustomSoftWrapIndent(), "softWraps.relativeIndent");
    addBoolIfDiffers(set, es, esDefault, s -> s.isAllSoftWrapsShown(), "softWraps.showAll");
    addIfDiffers(set, es, esDefault, s -> s.getStripTrailingSpaces(), "stripTrailingSpaces");
    addBoolIfDiffers(set, es, esDefault, s -> s.isEnsureNewLineAtEOF(), "ensureNewlineAtEOF");
    addBoolIfDiffers(set, es, esDefault, s -> s.isShowQuickDocOnMouseOverElement(), "quickDocOnMouseHover");
    addBoolIfDiffers(set, es, esDefault, s -> s.isBlinkCaret(), "blinkingCaret");
    addBoolIfDiffers(set, es, esDefault, s -> s.isBlockCursor(), "blockCaret");
    addBoolIfDiffers(set, es, esDefault, s -> s.isRightMarginShown(), "rightMargin");
    addBoolIfDiffers(set, es, esDefault, s -> s.isLineNumbersShown(), "lineNumbers");
    addBoolIfDiffers(set, es, esDefault, s -> s.areGutterIconsShown(), "gutterIcons");
    addBoolIfDiffers(set, es, esDefault, s -> s.isFoldingOutlineShown(), "foldingOutline");
    addBoolIfDiffers(set, es, esDefault, s -> s.isWhitespacesShown() && s.isLeadingWhitespacesShown(), "showLeadingWhitespace");
    addBoolIfDiffers(set, es, esDefault, s -> s.isWhitespacesShown() && s.isInnerWhitespacesShown(), "showInnerWhitespace");
    addBoolIfDiffers(set, es, esDefault, s -> s.isWhitespacesShown() && s.isTrailingWhitespacesShown(), "showTrailingWhitespace");
    addBoolIfDiffers(set, es, esDefault, s -> s.isIndentGuidesShown(), "indentGuides");
    addBoolIfDiffers(set, es, esDefault, s -> s.isSmoothScrolling(), "animatedScroll");
    addBoolIfDiffers(set, es, esDefault, s -> s.isDndEnabled(), "dragNDrop");
    addBoolIfDiffers(set, es, esDefault, s -> s.isWheelFontChangeEnabled(), "wheelZoom");
    addBoolIfDiffers(set, es, esDefault, s -> s.isMouseClickSelectionHonorsCamelWords(), "mouseCamel");
    addBoolIfDiffers(set, es, esDefault, s -> s.isVariableInplaceRenameEnabled(), "inplaceRename");
    addBoolIfDiffers(set, es, esDefault, s -> s.isPreselectRename(), "preselectOnRename");
    addBoolIfDiffers(set, es, esDefault, s -> s.isShowInlineLocalDialog(), "inlineDialog");
    addBoolIfDiffers(set, es, esDefault, s -> s.isRefrainFromScrolling(), "minimizeScrolling");
    addBoolIfDiffers(set, es, esDefault, s -> s.getOptions().SHOW_NOTIFICATION_AFTER_REFORMAT_CODE_ACTION, "afterReformatNotification");
    addBoolIfDiffers(set, es, esDefault, s -> s.getOptions().SHOW_NOTIFICATION_AFTER_OPTIMIZE_IMPORTS_ACTION, "afterOptimizeNotification");
    addBoolIfDiffers(set, es, esDefault, s -> s.isSmartHome(), "smartHome");
    addBoolIfDiffers(set, es, esDefault, s -> s.isCamelWords(), "camelWords");
    addBoolIfDiffers(set, es, esDefault, s -> s.isShowParameterNameHints(), "editor.inlay.parameter.hints");
    addBoolIfDiffers(set, es, esDefault, s -> s.isBreadcrumbsAbove(), "noBreadcrumbsBelow");
    addBoolIfDiffers(set, es, esDefault, s -> s.isBreadcrumbsShown(), "breadcrumbs");
    for (String language : es.getOptions().getLanguageBreadcrumbsMap().keySet()) {
      addBoolIfDiffers(set, es, esDefault, s -> s.isBreadcrumbsShownFor(language), "breadcrumbsFor" + language);
    }

    RichCopySettings rcs = RichCopySettings.getInstance();
    RichCopySettings rcsDefault = new RichCopySettings();
    addBoolIfDiffers(set, rcs, rcsDefault, s -> s.isEnabled(), "richCopy");

    CodeInsightSettings cis = CodeInsightSettings.getInstance();
    CodeInsightSettings cisDefault = new CodeInsightSettings();
    addBoolIfDiffers(set, cis, cisDefault, s -> s.AUTO_POPUP_PARAMETER_INFO, "parameterAutoPopup");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.AUTO_POPUP_JAVADOC_INFO, "javadocAutoPopup");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.AUTO_POPUP_COMPLETION_LOOKUP, "completionAutoPopup");
    addIfDiffers(set, cis, cisDefault, s -> s.COMPLETION_CASE_SENSITIVE, "completionCaseSensitivity");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS, "autoPopupCharComplete");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.AUTOCOMPLETE_ON_CODE_COMPLETION, "autoCompleteBasic");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION, "autoCompleteSmart");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO, "parameterInfoFullSignature");
    addIfDiffers(set, cis, cisDefault, s -> s.getBackspaceMode(), "smartBackspace");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.SMART_INDENT_ON_ENTER, "indentOnEnter");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.INSERT_BRACE_ON_ENTER, "braceOnEnter");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.JAVADOC_STUB_ON_ENTER, "javadocOnEnter");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.SMART_END_ACTION, "smartEnd");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.JAVADOC_GENERATE_CLOSING_TAG, "autoCloseJavadocTags");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.SURROUND_SELECTION_ON_QUOTE_TYPED, "surroundByQuoteOrBrace");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.AUTOINSERT_PAIR_BRACKET, "pairBracketAutoInsert");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.AUTOINSERT_PAIR_QUOTE, "pairQuoteAutoInsert");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.REFORMAT_BLOCK_ON_RBRACE, "reformatOnRBrace");
    addIfDiffers(set, cis, cisDefault, s -> s.REFORMAT_ON_PASTE, "reformatOnPaste");
    addIfDiffers(set, cis, cisDefault, s -> s.ADD_IMPORTS_ON_PASTE, "importsOnPaste");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.HIGHLIGHT_BRACES, "bracesHighlight");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.HIGHLIGHT_SCOPE, "scopeHighlight");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.HIGHLIGHT_IDENTIFIER_UNDER_CARET, "identifierUnderCaretHighlight");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY, "autoAddImports");
    addBoolIfDiffers(set, cis, cisDefault, s -> s.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION, "completionHints");
    
    return set;
  }

  private static <T> void addBoolIfDiffers(Set<UsageDescriptor> set,
                                           T settingsBean, T defaultSettingsBean, BooleanFunction<T> valueFunction, String featureId) {
    boolean value = valueFunction.fun(settingsBean);
    boolean defaultValue = valueFunction.fun(defaultSettingsBean);
    if (value != defaultValue) {
      set.add(new UsageDescriptor(defaultValue ? "no" + StringUtil.capitalize(featureId) : featureId, 1));
    }
  }

  private static <T> void addIfDiffers(Set<UsageDescriptor> set,
                                       T settingsBean, T defaultSettingsBean, Function<T, Object> valueFunction, String featureIdPrefix) {
    Object value = valueFunction.apply(settingsBean);
    Object defaultValue = valueFunction.apply(defaultSettingsBean);
    if (!Comparing.equal(value, defaultValue)) {
      set.add(new UsageDescriptor(featureIdPrefix + "." + value, 1));
    }
  }

  public static class ProjectUsages extends AbstractProjectsUsagesCollector {
    @NotNull
    @Override
    public GroupDescriptor getGroupId() {
      return GROUP_DESCRIPTOR;
    }

    @NotNull
    @Override
    public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) throws CollectUsagesException {
      Set<UsageDescriptor> set = new HashSet<>();
      CodeInsightWorkspaceSettings ciws = CodeInsightWorkspaceSettings.getInstance(project);
      CodeInsightWorkspaceSettings ciwsDefault = new CodeInsightWorkspaceSettings();
      addBoolIfDiffers(set, ciws, ciwsDefault, s -> s.optimizeImportsOnTheFly, "autoOptimizeImports");
      return set;
    }
  }

}
