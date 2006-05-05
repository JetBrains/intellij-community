/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.TextRange;
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
public final class Annotation {
  private final int myStartOffset;
  private final int myEndOffset;
  private final HighlightSeverity mySeverity;
  private final String myMessage;

  private ProblemHighlightType myHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
  private TextAttributesKey myEnforcedAttributes = null;


  public static class QuickFixInfo {
    public final IntentionAction quickFix;
    public final TextRange textRange;
    public final List<IntentionAction> options;
    public final String displayName;

    public QuickFixInfo(final IntentionAction quickFix, final TextRange textRange, final List<IntentionAction> options, String displayName) {
      this.quickFix = quickFix;
      this.textRange = textRange;
      this.options = options;
      this.displayName = displayName;
    }
  }
  private List<QuickFixInfo> myQuickFixes = null;
  private Boolean myNeedsUpdateOnTyping = null;
  private String myTooltip;
  private boolean myAfterEndOfLine = false;
  private boolean myIsFileLevelAnnotation = false;
  private GutterIconRenderer myGutterIconRenderer;

  /**
   * Creates an instance of the annotation.
   *
   * @param startOffset the start offset of the text range covered by the annotation.
   * @param endOffset   the end offset of the text range covered by the annotation.
   * @param severity    the severity of the problem indicated by the annotation (highlight, warning or error).
   * @param message     the description of the annotation (shown in the status bar or by "View | Error Description" action)
   * @param tooltip     the tooltip for the annotation (shown when hovering the mouse in the gutter bar)
   * @see AnnotationHolder#createErrorAnnotation
   * @see AnnotationHolder#createWarningAnnotation
   * @see AnnotationHolder#createInfoAnnotation
   */
  public Annotation(final int startOffset, final int endOffset, final HighlightSeverity severity, final String message, String tooltip) {
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
  public void registerFix(IntentionAction fix) {
    registerFix(fix, null);
  }

  public void registerFix(IntentionAction fix, TextRange range) {
    registerFix(fix,range,null, null);
  }

  /**
   * Registers a quick fix for the annotation which is only available on a particular range of text
   * within the annotation.
   *
   * @param fix   the quick fix implementation.
   * @param range the text range (relative to the document) where the quick fix is available.
   */
  public void registerFix(IntentionAction fix, TextRange range, List<IntentionAction> options, String displayName) {
    if (range == null) {
      range = new TextRange(myStartOffset, myEndOffset);
    }
    if (myQuickFixes == null) {
      myQuickFixes = new ArrayList<QuickFixInfo>();
    }
    myQuickFixes.add(new QuickFixInfo(fix, range, options, displayName));
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
    myNeedsUpdateOnTyping = Boolean.valueOf(b);
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
  public int getStartOffset() {
    return myStartOffset;
  }

  /**
   * Returns the end offset of the text range covered by the annotation.
   *
   * @return the annotation end offset.
   */
  public int getEndOffset() {
    return myEndOffset;
  }

  /**
   * Returns the severity of the problem indicated by the annotation (highlight, warning or error).
   *
   * @return the annotation severity.
   */
  public HighlightSeverity getSeverity() {
    return mySeverity;
  }

  /**
   * If the annotation matches one of commonly encountered problem types, returns the ID of that
   * problem type so that an appropriate color can be used for highlighting the annotation.
   *
   * @return the common problem type.
   */
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
  public TextAttributesKey getTextAttributes() {
    if (myEnforcedAttributes != null) return myEnforcedAttributes;

    if (myHighlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING) {
      if (mySeverity == HighlightSeverity.ERROR) return CodeInsightColors.ERRORS_ATTRIBUTES;
      if (mySeverity == HighlightSeverity.WARNING) return CodeInsightColors.WARNINGS_ATTRIBUTES;
      if (mySeverity == HighlightSeverity.INFO) return CodeInsightColors.INFO_ATTRIBUTES;
    }
    if (myHighlightType == ProblemHighlightType.LIKE_DEPRECATED) {
      return CodeInsightColors.DEPRECATED_ATTRIBUTES;
    }
    if (myHighlightType == ProblemHighlightType.LIKE_UNUSED_SYMBOL) {
      return CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES;
    }
    if (myHighlightType == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) {
      return CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES;
    }
    return HighlighterColors.TEXT;
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
  public void setTooltip(final String tooltip) {
    myTooltip = tooltip;
  }

  /**
   * If the annotation matches one of commonly encountered problem types, sets the ID of that
   * problem type so that an appropriate color can be used for highlighting the annotation.
   *
   * @param highlightType the ID of the problem type.
   */
  public void setHighlightType(final ProblemHighlightType highlightType) {
    myHighlightType = highlightType;
  }

  /**
   * Sets the text attributes key used for highlighting the annotation.
   *
   * @param enforcedAttributes the text attributes key for highlighting,
   */
  public void setTextAttributes(final TextAttributesKey enforcedAttributes) {
    myEnforcedAttributes = enforcedAttributes;
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
   * File level annoations are visualized differently than lesser range annotations by showing a title bar on top of the
   * editor rather than applying text attributes to the text range.
   * @return <code>true</code> if this particular annotation have been defined as file level.
   */
  public boolean isFileLevelAnnotation() {
    return myIsFileLevelAnnotation;
  }

  /**
   * File level annoations are visualized differently than lesser range annotations by showing a title bar on top of the
   * editor rather than applying text attributes to the text range.
   * @param isFileLevelAnnotation <code>true</code> if this particular annotation should be visualized at file level.
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
}
