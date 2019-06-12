// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementSectionRule;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Section.END_SECTION;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Section.START_SECTION;

/**
 * Class that is able to detect arrangement section start/end from comment element.
 * <p/>
 * The detection is based on arrangement settings.
 *
 * @author Svetlana.Zemlyanskaya
 */
public class ArrangementSectionDetector {
  private final Document myDocument;
  private final ArrangementSettings mySettings;
  private final Consumer<? super ArrangementSectionEntryTemplate> mySectionEntryProducer;
  private final Stack<ArrangementSectionRule> myOpenedSections = new Stack<>();

  public ArrangementSectionDetector(@Nullable Document document,
                                    @NotNull ArrangementSettings settings,
                                    @NotNull Consumer<? super ArrangementSectionEntryTemplate> producer) {
    myDocument = document;
    mySettings = settings;
    mySectionEntryProducer = producer;
  }

  /**
   * Check if comment can be recognized as section start/end
   * @return true for section comment, false otherwise
   */
  public boolean processComment(@NotNull PsiComment comment) {
    final TextRange range = comment.getTextRange();
    final TextRange expandedRange = myDocument == null ? range : ArrangementUtil.expandToLineIfPossible(range, myDocument);
    final TextRange sectionTextRange = new TextRange(expandedRange.getStartOffset(), expandedRange.getEndOffset());

    final String commentText = comment.getText().trim();
    final ArrangementSectionRule openSectionRule = isSectionStartComment(mySettings, commentText);
    if (openSectionRule != null) {
      mySectionEntryProducer.consume(new ArrangementSectionEntryTemplate(comment, START_SECTION, sectionTextRange, commentText));
      myOpenedSections.push(openSectionRule);
      return true;
    }

    if (!myOpenedSections.isEmpty()) {
      final ArrangementSectionRule lastSection = myOpenedSections.peek();
      if (lastSection.getEndComment() != null && StringUtil.equals(commentText, lastSection.getEndComment())) {
        mySectionEntryProducer.consume(new ArrangementSectionEntryTemplate(comment, END_SECTION, sectionTextRange, commentText));
        myOpenedSections.pop();
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static ArrangementSectionRule isSectionStartComment(@NotNull ArrangementSettings settings, @NotNull String comment) {
    for (ArrangementSectionRule rule : settings.getSections()) {
      if (rule.getStartComment() != null && StringUtil.equals(comment, rule.getStartComment())) {
        return rule;
      }
    }
    return null;
  }

  public static class ArrangementSectionEntryTemplate {
    private final PsiElement myElement;
    private final ArrangementSettingsToken myToken;
    private final TextRange myTextRange;
    private final String myText;

    public ArrangementSectionEntryTemplate(@NotNull PsiElement element,
                                           @NotNull ArrangementSettingsToken token,
                                           @NotNull TextRange range,
                                           @NotNull String text) {
      myElement = element;
      myToken = token;
      myTextRange = range;
      myText = text;
    }

    @NotNull
    public PsiElement getElement() {
      return myElement;
    }

    @NotNull
    public ArrangementSettingsToken getToken() {
      return myToken;
    }

    @NotNull
    public TextRange getTextRange() {
      return myTextRange;
    }

    @NotNull
    public String getText() {
      return myText;
    }
  }
}
