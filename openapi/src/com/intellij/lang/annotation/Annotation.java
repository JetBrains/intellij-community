package com.intellij.lang.annotation;

import com.intellij.codeInsight.CodeInsightColors;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 3, 2005
 * Time: 6:42:42 PM
 * To change this template use File | Settings | File Templates.
 */
public final class Annotation {
  private final int myStartOffset;
  private final int myEndOffset;
  private final HighlightSeverity mySeverity;
  private final String myMessage;

  private ProblemHighlightType myHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
  private TextAttributesKey myEnforcedAttributes = null;
  private List<Pair<IntentionAction, TextRange>> myQuickFixes = null;
  private Boolean myNeedsUpdateOnTyping = null;
  private String myTooltip;
  private boolean myAfterEndOfLine = false;

  public Annotation(final int startOffset, final int endOffset, final HighlightSeverity severity, final String message, String tooltip) {
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myMessage = message;
    myTooltip = tooltip;
    mySeverity = severity;
  }

  public void registerFix(IntentionAction fix) {
    registerFix(fix, null);
  }

  public void registerFix(IntentionAction fix, TextRange range) {
    if (range == null) {
      range = new TextRange(myStartOffset, myEndOffset);
    }
    if (myQuickFixes == null) {
      myQuickFixes = new ArrayList<Pair<IntentionAction, TextRange>>();
    }
    myQuickFixes.add(new Pair<IntentionAction, TextRange>(fix, range));
  }

  public void setNeedsUpdateOnTyping(boolean b) {
    myNeedsUpdateOnTyping = Boolean.valueOf(b);
  }

  public boolean needsUpdateOnTyping() {
    if (myNeedsUpdateOnTyping == null) {
      return mySeverity != HighlightSeverity.INFORMATION;
    }

    return myNeedsUpdateOnTyping.booleanValue();
  }

  public int getStartOffset() {
    return myStartOffset;
  }

  public int getEndOffset() {
    return myEndOffset;
  }

  public HighlightSeverity getSeverity() {
    return mySeverity;
  }

  public ProblemHighlightType getHighlightType() {
    return myHighlightType;
  }

  public TextAttributesKey getTextAttributes() {
    if (myEnforcedAttributes != null) return myEnforcedAttributes;

    if (myHighlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING) {
      if (mySeverity == HighlightSeverity.ERROR) return CodeInsightColors.ERRORS_ATTRIBUTES;
      if (mySeverity == HighlightSeverity.WARNING) return CodeInsightColors.WARNINGS_ATTRIBUTES;
    }
    else if (myHighlightType == ProblemHighlightType.LIKE_DEPRECATED) {
      return CodeInsightColors.DEPRECATED_ATTRIBUTES;
    }
    else if (myHighlightType == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) {
      return CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES;
    }
    else if (myHighlightType == ProblemHighlightType.LIKE_UNUSED_SYMBOL) {
      return CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES;
    }
    return HighlighterColors.TEXT;
  }

  public List<Pair<IntentionAction, TextRange>> getQuickFixes() {
    return myQuickFixes;
  }

  public String getMessage() {
    return myMessage;
  }

  public String getTooltip() {
    return myTooltip;
  }

  public void setTooltip(final String tooltip) {
    myTooltip = tooltip;
  }

  public void setHighlightType(final ProblemHighlightType highlightType) {
    myHighlightType = highlightType;
  }

  public void setTextAttributes(final TextAttributesKey enforcedAttributes) {
    myEnforcedAttributes = enforcedAttributes;
  }

  public boolean isAfterEndOfLine() {
    return myAfterEndOfLine;
  }

  public void setAfterEndOfLine(final boolean afterEndOfLine) {
    myAfterEndOfLine = afterEndOfLine;
  }
}
