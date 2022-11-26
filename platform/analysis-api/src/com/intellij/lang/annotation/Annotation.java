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
  private final @NlsContexts.DetailedDescription String myMessage;

  private ProblemHighlightType myHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
  private TextAttributesKey myEnforcedAttributesKey;
  private TextAttributes myEnforcedAttributes;

  private List<QuickFixInfo> myQuickFixes;
  private Boolean myNeedsUpdateOnTyping;
  private @NlsContexts.Tooltip String myTooltip;
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
   * Do not create Annotation manually, please use {@link AnnotationHolder#newAnnotation(HighlightSeverity, String)} builder methods instead,
   * in order to show the annotation faster.
   * @param startOffset the start offset of the text range covered by the annotation.
   * @param endOffset   the end offset of the text range covered by the annotation.
   * @param severity    the severity of the problem indicated by the annotation (highlight, warning or error).
   * @param message     the description of the annotation (shown in the status bar or by "View | Error Description" action)
   * @param tooltip     the tooltip for the annotation (shown when hovering the mouse in the gutter bar)
   * @see AnnotationHolder#newAnnotation
   * @deprecated use {@link AnnotationHolder#newAnnotation} instead
   */
  @ApiStatus.Internal
  @Deprecated
  public Annotation(int startOffset,
                    int endOffset,
                    @NotNull HighlightSeverity severity,
                    @NlsContexts.DetailedDescription String message,
                    @NlsContexts.Tooltip String tooltip) {
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
   * @deprecated use {@link AnnotationBuilder#newFix(IntentionAction)} instead
   *
   * @param fix the quick fix implementation.
   */
  @Deprecated
  public void registerFix(@NotNull IntentionAction fix) {
    registerFix(fix, null);
  }

  /**
   * Registers a quick fix {@code fix} for this annotation, which is triggerable in the with specified text {@code range}
   * @deprecated use {@link AnnotationBuilder#newFix(IntentionAction)} instead
   *
   * @param fix the quick fix implementation.
   * @param range the text range within which the quick fix can be triggered
   */
  @Deprecated
  public void registerFix(@NotNull IntentionAction fix, TextRange range) {
    registerFix(fix,range, null);
  }

  /**
   * Registers a quick fix {@code fix} for this annotation, which is triggerable in the with specified text {@code range}
   * with specific key and descriptor
   * @deprecated use {@link AnnotationBuilder#newFix(IntentionAction)} instead
   *
   * @param fix the quick fix implementation.
   * @param range the text range within which the quick fix can be triggered
   * @param key HighlightDisplayKey of the inspection which provided this fix
   * @param problemDescriptor ProblemDescriptor of the problem created by the inspection with this fix
   */
  @Deprecated
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
   * @deprecated use {@link AnnotationBuilder#newFix(IntentionAction)} instead
   *
   * @param fix   the quick fix implementation.
   * @param range the text range (relative to the document) where the quick fix is available.
   * @param key HighlightDisplayKey of the inspection which provided this fix
   */
  @Deprecated
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
   * @deprecated use {@link AnnotationBuilder#newFix(IntentionAction)} instead
   *
   * @param fix   the quick fix implementation.
   * @param range the text range (relative to the document) where the quick fix is available.
   * @param key HighlightDisplayKey of the inspection which provided this fix
   */
  @Deprecated
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
   * @deprecated use {@link AnnotationBuilder#newFix(IntentionAction)} instead
   */
  @Deprecated
  public <T extends IntentionAction & LocalQuickFix> void registerUniversalFix(@NotNull T fix, @Nullable TextRange range, @Nullable final HighlightDisplayKey key) {
    registerBatchFix(fix, range, key);
    registerFix(fix, range, key);
  }
  /**
   * Sets a flag indicating what happens with the annotation when the user starts typing.
   * If the parameter is true, the annotation is removed as soon as the user starts typing
   * and is possibly restored by a later run of the annotator. If false, the annotation remains
   * in place while the user is typing.
   * @deprecated  use {@link AnnotationBuilder#needsUpdateOnTyping(boolean)} instead
   *
   * @param b whether the annotation needs to be removed on typing.
   * @see #needsUpdateOnTyping()
   */
  @Deprecated
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

    return switch (myHighlightType) {
      case GENERIC_ERROR_OR_WARNING -> {
        if (mySeverity == HighlightSeverity.ERROR) yield CodeInsightColors.ERRORS_ATTRIBUTES;
        if (mySeverity == HighlightSeverity.WARNING) yield CodeInsightColors.WARNINGS_ATTRIBUTES;
        if (mySeverity == HighlightSeverity.WEAK_WARNING) yield CodeInsightColors.WEAK_WARNING_ATTRIBUTES;
        yield HighlighterColors.NO_HIGHLIGHTING;
      }
      case GENERIC_ERROR -> CodeInsightColors.ERRORS_ATTRIBUTES;
      case LIKE_DEPRECATED -> CodeInsightColors.DEPRECATED_ATTRIBUTES;
      case LIKE_UNUSED_SYMBOL -> CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES;
      case LIKE_UNKNOWN_SYMBOL, ERROR -> CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES;
      default -> HighlighterColors.NO_HIGHLIGHTING;
    };
  }

  public TextAttributes getEnforcedTextAttributes() {
    return myEnforcedAttributes;
  }

  /**
   * Sets the text attributes used for highlighting the annotation.
   * @deprecated  use {@link AnnotationBuilder#enforcedTextAttributes(TextAttributes)} instead
   *
   * @param enforcedAttributes the text attributes for highlighting,
   */
  @Deprecated
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
  public @NlsContexts.DetailedDescription String getMessage() {
    return myMessage;
  }

  /**
   * Returns the tooltip for the annotation (shown when hovering the mouse in the gutter bar).
   *
   * @return the tooltip for the annotation.
   */
  public @NlsContexts.Tooltip String getTooltip() {
    return myTooltip;
  }

  /**
   * Sets the tooltip for the annotation (shown when hovering the mouse in the gutter bar).
   * @deprecated Use {@link AnnotationBuilder#tooltip(String)} instead
   *
   * @param tooltip the tooltip text.
   */
  @Deprecated
  public void setTooltip(@NlsContexts.Tooltip String tooltip) {
    myTooltip = tooltip;
  }

  /**
   * If the annotation matches one of commonly encountered problem types, sets the ID of that
   * problem type so that an appropriate color can be used for highlighting the annotation.
   * @deprecated use {@link AnnotationBuilder#highlightType(ProblemHighlightType)} isntead
   *
   * @param highlightType the ID of the problem type.
   */
  @Deprecated
  public void setHighlightType(@NotNull ProblemHighlightType highlightType) {
    myHighlightType = highlightType;
  }

  /**
   * Sets the text attributes key used for highlighting the annotation.
   * @deprecated use {@link AnnotationBuilder#textAttributes(TextAttributesKey)} instead
   *
   * @param enforcedAttributes the text attributes key for highlighting,
   */
  @Deprecated
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
   * @deprecated  use {@link AnnotationBuilder#afterEndOfLine()} instead
   *
   * @param afterEndOfLine true if the annotation should be shown after the end of line, false otherwise.
   */
  @Deprecated
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
   * @deprecated Use {@link AnnotationBuilder#fileLevel()} instead
   * @param isFileLevelAnnotation {@code true} if this particular annotation should be visualized at file level.
   */
  @Deprecated
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
   * @deprecated use {@link AnnotationBuilder#gutterIconRenderer(GutterIconRenderer)} instead
   *
   * @param gutterIconRenderer the gutter icon renderer instance.
   */
  @Deprecated
  public void setGutterIconRenderer(@Nullable final GutterIconRenderer gutterIconRenderer) {
    myGutterIconRenderer = gutterIconRenderer;
  }

  /**
   * Gets the unique object, which is the same for all the problems of this group
   *
   * @return the problem group
   */
  @Nullable
  public ProblemGroup getProblemGroup() {
    return myProblemGroup;
  }

  /**
   * Sets the unique object, which is the same for all the problems of this group
   * @deprecated use {@link AnnotationBuilder#problemGroup(ProblemGroup)} instead
   *
   * @param problemGroup the problem group
   */
  @Deprecated
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
