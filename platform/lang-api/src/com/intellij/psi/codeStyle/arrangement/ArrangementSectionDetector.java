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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementSectionRule;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
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
  private final Consumer<ArrangementSectionEntryTemplate> mySectionEntryProducer;
  private Stack<String> myOpenedSections = ContainerUtil.newStack();

  public ArrangementSectionDetector(@Nullable Document document,
                                    @NotNull ArrangementSettings settings,
                                    @NotNull Consumer<ArrangementSectionEntryTemplate> producer) {
    myDocument = document;
    mySettings = settings;
    mySectionEntryProducer = producer;
  }

  public void processComment(@NotNull PsiComment comment) {
    final TextRange range = comment.getTextRange();
    final TextRange expandedRange = myDocument == null ? range : ArrangementUtil.expandToLineIfPossible(range, myDocument);
    final TextRange sectionTextRange = new TextRange(expandedRange.getStartOffset(), expandedRange.getEndOffset());

    final String commentText = comment.getText().trim();
    final boolean start = isSectionStartComment(mySettings, commentText);
    String lastOpenedSection = myOpenedSections.isEmpty() ? null : myOpenedSections.peek();
    if (start) {
      mySectionEntryProducer.consume(new ArrangementSectionEntryTemplate(comment, START_SECTION, sectionTextRange, commentText));
      myOpenedSections.push(commentText);
    }
    else if (lastOpenedSection != null && isSectionEndComment(mySettings, commentText)) {
      mySectionEntryProducer.consume(new ArrangementSectionEntryTemplate(comment, END_SECTION, sectionTextRange, commentText));
      myOpenedSections.pop();
    }
  }

  public static boolean isSectionStartComment(@NotNull ArrangementSettings settings, @NotNull String comment) {
    for (ArrangementSectionRule rule : settings.getSections()) {
      if (rule.getStartComment() != null && StringUtil.equals(comment, rule.getStartComment())) {
        return true;
      }
    }
    return false;
  }

  public static boolean isSectionEndComment(@NotNull ArrangementSettings settings, @NotNull String comment) {
    for (ArrangementSectionRule rule : settings.getSections()) {
      if (rule.getEndComment() != null && StringUtil.equals(comment, rule.getEndComment())) {
        return true;
      }
    }
    return false;
  }

  public static class ArrangementSectionEntryTemplate {
    private PsiElement myElement;
    private ArrangementSettingsToken myToken;
    private TextRange myTextRange;
    private String myText;

    public ArrangementSectionEntryTemplate(@NotNull PsiElement element,
                                           @NotNull ArrangementSettingsToken token,
                                           @NotNull TextRange range,
                                           @NotNull String text) {
      myElement = element;
      myToken = token;
      myTextRange = range;
      myText = text;
    }

    public PsiElement getElement() {
      return myElement;
    }

    public ArrangementSettingsToken getToken() {
      return myToken;
    }

    public TextRange getTextRange() {
      return myTextRange;
    }

    public String getText() {
      return myText;
    }
  }
}
