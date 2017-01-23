/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeInsight.editorActions.SmartBackspaceMode;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.editor.richcopy.settings.RichCopySettings;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

class EditorSettingsStatisticsCollector extends UsagesCollector {
  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("Editor");
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() throws CollectUsagesException {
    Set<UsageDescriptor> set = new HashSet<>();
    
    EditorSettingsExternalizable es = EditorSettingsExternalizable.getInstance();
    addIfDiffers(set, es.isVirtualSpace(), false, "caretAfterLineEnd");
    addIfDiffers(set, es.isCaretInsideTabs(), false, "caretInsideTabs");
    addIfDiffers(set, es.isAdditionalPageAtBottom(), false, "virtualSpaceAtFileBottom");
    addIfDiffers(set, es.isUseSoftWraps(SoftWrapAppliancePlaces.MAIN_EDITOR), false, "softWraps");
    addIfDiffers(set, es.isUseSoftWraps(SoftWrapAppliancePlaces.CONSOLE), false, "softWraps.console");
    addIfDiffers(set, es.isUseSoftWraps(SoftWrapAppliancePlaces.PREVIEW), false, "softWraps.preview");
    addIfDiffers(set, es.isUseCustomSoftWrapIndent(), false, "softWraps.relativeIndent");
    addIfDiffers(set, es.isAllSoftWrapsShown(), false, "softWraps.showAll");
    addIfDiffers(set, es.getStripTrailingSpaces(), EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED, "stripTrailingSpaces");
    addIfDiffers(set, es.isEnsureNewLineAtEOF(), false, "ensureNewlineAtEOF");
    addIfDiffers(set, es.isShowQuickDocOnMouseOverElement(), false, "quickDocOnMouseHover");
    addIfDiffers(set, es.isBlinkCaret(), true, "blinkingCaret");
    addIfDiffers(set, es.isBlockCursor(), false, "blockCaret");
    addIfDiffers(set, es.isRightMarginShown(), true, "rightMargin");
    addIfDiffers(set, es.isLineNumbersShown(), true, "lineNumbers");
    addIfDiffers(set, es.areGutterIconsShown(), true, "gutterIcons");
    addIfDiffers(set, es.isFoldingOutlineShown(), true, "foldingOutline");
    addIfDiffers(set, es.isWhitespacesShown() && es.isLeadingWhitespacesShown(), false, "showLeadingWhitespace");
    addIfDiffers(set, es.isWhitespacesShown() && es.isInnerWhitespacesShown(), false, "showInnerWhitespace");
    addIfDiffers(set, es.isWhitespacesShown() && es.isTrailingWhitespacesShown(), false, "showTrailingWhitespace");
    addIfDiffers(set, es.isIndentGuidesShown(), true, "indentGuides");
    addIfDiffers(set, es.isSmoothScrolling(), true, "animatedScroll");
    addIfDiffers(set, es.isDndEnabled(), true, "dragNDrop");
    addIfDiffers(set, es.isWheelFontChangeEnabled(), false, "wheelZoom");
    addIfDiffers(set, es.isMouseClickSelectionHonorsCamelWords(), true, "mouseCamel");
    addIfDiffers(set, es.isVariableInplaceRenameEnabled(), true, "inplaceRename");
    addIfDiffers(set, es.isPreselectRename(), true, "preselectOnRename");
    addIfDiffers(set, es.isShowInlineLocalDialog(), true, "inlineDialog");
    addIfDiffers(set, es.isRefrainFromScrolling(), false, "minimizeScrolling");
    addIfDiffers(set, es.getOptions().SHOW_NOTIFICATION_AFTER_REFORMAT_CODE_ACTION, true, "afterReformatNotification");
    addIfDiffers(set, es.getOptions().SHOW_NOTIFICATION_AFTER_OPTIMIZE_IMPORTS_ACTION, true, "afterOptimizeNotification");
    addIfDiffers(set, es.isSmartHome(), true, "smartHome");
    addIfDiffers(set, es.isCamelWords(), false, "camelWords");
    addIfDiffers(set, es.isShowParameterNameHints(), true, "editor.inlay.parameter.hints");

    RichCopySettings rcs = RichCopySettings.getInstance();
    addIfDiffers(set, rcs.isEnabled(), true, "richCopy");

    CodeInsightSettings cis = CodeInsightSettings.getInstance();
    addIfDiffers(set, cis.AUTO_POPUP_PARAMETER_INFO, true, "parameterAutoPopup");
    addIfDiffers(set, cis.AUTO_POPUP_JAVADOC_INFO, false, "javadocAutoPopup");
    addIfDiffers(set, cis.AUTO_POPUP_COMPLETION_LOOKUP, true, "completionAutoPopup");
    addIfDiffers(set, cis.COMPLETION_CASE_SENSITIVE, CodeInsightSettings.FIRST_LETTER, "completionCaseSensitivity");
    addIfDiffers(set, cis.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS, false, "autoPopupCharComplete");
    addIfDiffers(set, cis.AUTOCOMPLETE_ON_CODE_COMPLETION, true, "autoCompleteBasic");
    addIfDiffers(set, cis.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION, true, "autoCompleteSmart");
    addIfDiffers(set, cis.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO, false, "parameterInfoFullSignature");
    addIfDiffers(set, cis.getBackspaceMode(), SmartBackspaceMode.AUTOINDENT, "smartBackspace");
    addIfDiffers(set, cis.SMART_INDENT_ON_ENTER, true, "indentOnEnter");
    addIfDiffers(set, cis.INSERT_BRACE_ON_ENTER, true, "braceOnEnter");
    addIfDiffers(set, cis.JAVADOC_STUB_ON_ENTER, true, "javadocOnEnter");
    addIfDiffers(set, cis.SMART_END_ACTION, true, "smartEnd");
    addIfDiffers(set, cis.JAVADOC_GENERATE_CLOSING_TAG, true, "autoCloseJavadocTags");
    addIfDiffers(set, cis.SURROUND_SELECTION_ON_QUOTE_TYPED, false, "surroundByQuoteOrBrace");
    addIfDiffers(set, cis.AUTOINSERT_PAIR_BRACKET, true, "pairBracketAutoInsert");
    addIfDiffers(set, cis.AUTOINSERT_PAIR_QUOTE, true, "pairQuoteAutoInsert");
    addIfDiffers(set, cis.REFORMAT_BLOCK_ON_RBRACE, true, "reformatOnRBrace");
    addIfDiffers(set, cis.REFORMAT_ON_PASTE, CodeInsightSettings.INDENT_EACH_LINE, "reformatOnPaste");
    addIfDiffers(set, cis.ADD_IMPORTS_ON_PASTE, CodeInsightSettings.ASK, "importsOnPaste");
    addIfDiffers(set, cis.HIGHLIGHT_BRACES, true, "bracesHighlight");
    addIfDiffers(set, cis.HIGHLIGHT_SCOPE, false, "scopeHighlight");
    addIfDiffers(set, cis.HIGHLIGHT_IDENTIFIER_UNDER_CARET, true, "identifierUnderCaretHighlight");
    addIfDiffers(set, cis.OPTIMIZE_IMPORTS_ON_THE_FLY, false, "autoOptimizeImports");
    addIfDiffers(set, cis.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY, false, "autoAddImports");
    
    return set;
  }

  private static void addIfDiffers(Set<UsageDescriptor> set, boolean value, boolean defaultValue, String featureId) {
    if (value != defaultValue) {
      set.add(new UsageDescriptor(defaultValue ? "no" + StringUtil.capitalize(featureId) : featureId, 1));
    }
  }

  private static void addIfDiffers(Set<UsageDescriptor> set, Object value, Object defaultValue, String featureIdPrefix) {
    if (!Comparing.equal(value, defaultValue)) {
      set.add(new UsageDescriptor(featureIdPrefix + "." + value, 1));
    }
  }
}
