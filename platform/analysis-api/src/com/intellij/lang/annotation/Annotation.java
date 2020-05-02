/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.annotation;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAsIntentionAdapter;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines an annotation, which is displayed as a gutter bar mark or an extra highlight in the editor.
 *
 * @author max
 * @see Annotator
 * @see AnnotationHolder
 * @see com.intellij.openapi.editor.markup.RangeHighlighter
 */
public final class Annotation implements Segment {
  private final int myStartOffset;
  private final int myEndOffset;
  private final HighlightSeverity mySeverity;
  private final String myMessage;

  private ProblemHighlightType myHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
  private TextAttributesKey myEnforcedAttributesKey;
  private TextAttributes myEnforcedAttributes;

  private List<QuickFixInfo> myQuickFixes;
  private Boolean myNeedsUpdateOnTyping;
  private String myTooltip;
  private boolean myAfterEndOfLine;
  private boolean myIsFileLevelAnnotation;
  private GutterIconRenderer myGutterIconRenderer;
  @Nullable
  private ProblemGroup myProblemGroup;
  private List<QuickFixInfo> myBatchFixes;

  public static class QuickFixInfo {
    @NotNull
    public final IntentionAction quickFix;
    @NotNull
    public final TextRange textRange;
    public final HighlightDisplayKey key;

    QuickFixInfo(@NotNull IntentionAction fix, @NotNull TextRange range, @Nullable final HighlightDisplayKey key) {
      this.key = key;
      quickFix = fix;
      textRange = range;
    }

    @Override
    public String toString() {
      return quickFix.toString();
    }
  }

  /**
   * Creates an instance of the annotation.
   * Do not create Annotation manually, please use {@link AnnotationHolder#newAnnotation(HighlightSeverity, String)} builder methods instead.
   * @param startOffset the start offset of the text range covered by the annotation.
   * @param endOffset   the end offset of the text range covered by the annotation.
   * @param severity    the severity of the problem indicated by the annotation (highlight, warning or error).
   * @param message     the description of the annotation (shown in the status bar or by "View | Error Description" action)
   * @param tooltip     the tooltip for the annotation (shown when hovering the mouse in the gutter bar)
   * @see AnnotationHolder#newAnnotation
   */
  @ApiStatus.Internal
  public Annotation(final int startOffset, final int endOffset, @NotNull HighlightSeverity severity, final String message, String tooltip) {
    assert startOffset <= endOffset : startOffset + ":" + endOffset;
    assert startOffset >= 0 : "Start offset must not be negative: " +startOffset;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myMessage = message;
    myTooltip = tooltip;
    mySeverity = severity;
  }

  /**
   * Registers a quick fix for the annotation.
   *
   * @param fix the quick fix implementation.
   */
  public void registerFix(@NotNull IntentionAction fix) {
    registerFix(fix, null);
  }

  public void registerFix(@NotNull IntentionAction fix, TextRange range) {
    registerFix(fix,range, null);
  }

  public void registerFix(@NotNull LocalQuickFix fix, @Nullable TextRange range, @Nullable HighlightDisplayKey key,
                          @NotNull ProblemDescriptor problemDescriptor) {
    range = notNullize(range);
    if (myQuickFixes == null) {
      myQuickFixes = new ArrayList<>();
    }
    myQuickFixes.add(new QuickFixInfo(new LocalQuickFixAsIntentionAdapter(fix, problemDescriptor), range, key));
  }

  /**
   * Registers a quick fix for the annotation which is only available on a particular range of text
   * within the annotation.
   *
   * @param fix   the quick fix implementation.
   * @param range the text range (relative to the document) where the quick fix is available.
   */
  public void registerFix(@NotNull IntentionAction fix, @Nullable TextRange range, @Nullable final HighlightDisplayKey key) {
    range = notNullize(range);
    List<QuickFixInfo> fixes = myQuickFixes;
    if (fixes == null) {
      myQuickFixes = fixes = new ArrayList<>();
    }
    fixes.add(new QuickFixInfo(fix, range, key));
  }

  @NotNull
  private TextRange notNullize(@Nullable TextRange range) {
    return range == null ? new TextRange(myStartOffset, myEndOffset) : range;
  }

  /**
   * Registers a quickfix which would be available during batch mode only,
   * in particular during com.intellij.codeInspection.DefaultHighlightVisitorBasedInspection run
   */
  public <T extends IntentionAction & LocalQuickFix> void registerBatchFix(@NotNull T fix, @Nullable TextRange range, @Nullable HighlightDisplayKey key) {
    range = notNullize(range);

    List<QuickFixInfo> fixes = myBatchFixes;
    if (fixes == null) {
      myBatchFixes = fixes = new ArrayList<>();
    }
    fixes.add(new QuickFixInfo(fix, range, key));
  }

  /**
   * Register a quickfix which would be available onTheFly and in the batch mode. Should implement both IntentionAction and LocalQuickFix.
   */
  public <T extends IntentionAction & LocalQuickFix> void registerUniversalFix(@NotNull T fix, @Nullable TextRange range, @Nullable final HighlightDisplayKey key) {
    registerBatchFix(fix, range, key);
    registerFix(fix, range, key);
  }
  /**
   * Sets a flag indicating what happens with the annotation when the user starts typing.
   * If the parameter is true, the annotation is removed as soon as the user starts typing
   * and is possibly restored by a later run of the annotator. If false, the annotation remains
   * in place while the user is typing.
   *
   * @param b whether the annotation needs to be removed on typing.
   * @see #needsUpdateOnTyping()
   */
  public void setNeedsUpdateOnTyping(boolean b) {
    myNeedsUpdateOnTyping = b;
  }

  /**
   * Gets a flag indicating what happens with the annotation when the user starts typing.
   *
   * @return true if the annotation is removed on typing, false otherwise.
   * @see #setNeedsUpdateOnTyping(boolean)
   */
  public boolean needsUpdateOnTyping() {
    if (myNeedsUpdateOnTyping == null) {
      return mySeverity != HighlightSeverity.INFORMATION;
    }

    return myNeedsUpdateOnTyping.booleanValue();
  }

  /**
   * Returns the start offset of the text range covered by the annotation.
   *
   * @return the annotation start offset.
   */
  @Override
  public int getStartOffset() {
    return myStartOffset;
  }

  /**
   * Returns the end offset of the text range covered by the annotation.
   *
   * @return the annotation end offset.
   */
  @Override
  public int getEndOffset() {
    return myEndOffset;
  }

  /**
   * Returns the severity of the problem indicated by the annotation (highlight, warning or error).
   *
   * @return the annotation severity.
   */
  @NotNull
  public HighlightSeverity getSeverity() {
    return mySeverity;
  }

  /**
   * If the annotation matches one of commonly encountered problem types, returns the ID of that
   * problem type so that an appropriate color can be used for highlighting the annotation.
   *
   * @return the common problem type.
   */
  @NotNull
  public ProblemHighlightType getHighlightType() {
    return myHighlightType;
  }

  /**
   * Returns the text attribute key used for highlighting the annotation. If not specified
   * explicitly, the key is determined automatically based on the problem highlight type and
   * the annotation severity.
   *
   * @return the text attribute key used for highlighting
   */
  @NotNull
  public TextAttributesKey getTextAttributes() {
    if (myEnforcedAttributesKey != null) return myEnforcedAttributesKey;

    switch (myHighlightType) {
      case GENERIC_ERROR_OR_WARNING:
        if (mySeverity == HighlightSeverity.ERROR) return CodeInsightColors.ERRORS_ATTRIBUTES;
        if (mySeverity == HighlightSeverity.WARNING) return CodeInsightColors.WARNINGS_ATTRIBUTES;
        if (mySeverity == HighlightSeverity.WEAK_WARNING) return CodeInsightColors.WEAK_WARNING_ATTRIBUTES;
        return HighlighterColors.NO_HIGHLIGHTING;
      case GENERIC_ERROR:
        return CodeInsightColors.ERRORS_ATTRIBUTES;
      case LIKE_DEPRECATED:
        return CodeInsightColors.DEPRECATED_ATTRIBUTES;
      case LIKE_UNUSED_SYMBOL:
        return CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES;
      case LIKE_UNKNOWN_SYMBOL:
      case ERROR:
        return CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES;
      default:
        return HighlighterColors.NO_HIGHLIGHTING;
    }
  }

  public TextAttributes getEnforcedTextAttributes() {
    return myEnforcedAttributes;
  }

  /**
   * Sets the text attributes used for highlighting the annotation.
   *
   * @param enforcedAttributes the text attributes for highlighting,
   */
  public void setEnforcedTextAttributes(final TextAttributes enforcedAttributes) {
    myEnforcedAttributes = enforcedAttributes;
  }

  /**
   * Returns the list of quick fixes registered for the annotation.
   *
   * @return the list of quick fixes, or null if none have been registered.
   */

  @Nullable
  public List<QuickFixInfo> getQuickFixes() {
    return myQuickFixes;
  }

  @Nullable
  public List<QuickFixInfo> getBatchFixes() {
    return myBatchFixes;
  }

  /**
   * Returns the description of the annotation (shown in the status bar or by "View | Error Description" action).
   *
   * @return the description of the annotation.
   */
  public String getMessage() {
    return myMessage;
  }

  /**
   * Returns the tooltip for the annotation (shown when hovering the mouse in the gutter bar).
   *
   * @return the tooltip for the annotation.
   */
  public String getTooltip() {
    return myTooltip;
  }

  /**
   * Sets the tooltip for the annotation (shown when hovering the mouse in the gutter bar).
   *
   * @param tooltip the tooltip text.
   */
  public void setTooltip(@NlsContexts.Tooltip String tooltip) {
    myTooltip = tooltip;
  }

  /**
   * If the annotation matches one of commonly encountered problem types, sets the ID of that
   * problem type so that an appropriate color can be used for highlighting the annotation.
   *
   * @param highlightType the ID of the problem type.
   */
  public void setHighlightType(@NotNull ProblemHighlightType highlightType) {
    myHighlightType = highlightType;
  }

  /**
   * Sets the text attributes key used for highlighting the annotation.
   *
   * @param enforcedAttributes the text attributes key for highlighting,
   */
  public void setTextAttributes(final TextAttributesKey enforcedAttributes) {
    myEnforcedAttributesKey = enforcedAttributes;
  }

  /**
   * Returns the flag indicating whether the annotation is shown after the end of line containing it.
   *
   * @return true if the annotation is shown after the end of line, false otherwise.
   */
  public boolean isAfterEndOfLine() {
    return myAfterEndOfLine;
  }

  /**
   * Sets the flag indicating whether the annotation is shown after the end of line containing it.
   * This can be used for errors like "unclosed string literal", "missing semicolon" and so on.
   *
   * @param afterEndOfLine true if the annotation should be shown after the end of line, false otherwise.
   */
  public void setAfterEndOfLine(final boolean afterEndOfLine) {
    myAfterEndOfLine = afterEndOfLine;
  }

  /**
   * File level annotations are visualized differently than lesser range annotations by showing a title bar on top of the
   * editor rather than applying text attributes to the text range.
   * @return {@code true} if this particular annotation have been defined as file level.
   */
  public boolean isFileLevelAnnotation() {
    return myIsFileLevelAnnotation;
  }

  /**
   * File level annotations are visualized differently than lesser range annotations by showing a title bar on top of the
   * editor rather than applying text attributes to the text range.
   * @param isFileLevelAnnotation {@code true} if this particular annotation should be visualized at file level.
   */
  public void setFileLevelAnnotation(final boolean isFileLevelAnnotation) {
    myIsFileLevelAnnotation = isFileLevelAnnotation;
  }

  /**
   * Gets the renderer used to draw the gutter icon in the region covered by the annotation.
   *
   * @return the gutter icon renderer instance.
   */
  @Nullable
  public GutterIconRenderer getGutterIconRenderer() {
    return myGutterIconRenderer;
  }

  /**
   * Sets the renderer used to draw the gutter icon in the region covered by the annotation.
   *
   * @param gutterIconRenderer the gutter icon renderer instance.
   */
  public void setGutterIconRenderer(@Nullable final GutterIconRenderer gutterIconRenderer) {
    myGutterIconRenderer = gutterIconRenderer;
  }

  /**
   * Gets the unique object, which is the same for all of the problems of this group
   *
   * @return the problem group
   */
  @Nullable
  public ProblemGroup getProblemGroup() {
    return myProblemGroup;
  }

  /**
   * Sets the unique object, which is the same for all of the problems of this group
   *
   * @param problemGroup the problem group
   */
  public void setProblemGroup(@Nullable ProblemGroup problemGroup) {
    myProblemGroup = problemGroup;
  }

  @NonNls
  public String toString() {
    return "Annotation(" +
           "message='" + myMessage + "'" +
           ", severity='" + mySeverity + "'" +
           ", toolTip='" + myTooltip + "'" +
           ")";
  }
}
