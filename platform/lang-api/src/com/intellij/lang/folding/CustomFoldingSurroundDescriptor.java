/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.lang.folding;

import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Rustam Vishnyakov
 */
public class CustomFoldingSurroundDescriptor implements SurroundDescriptor {

  public final static CustomFoldingSurroundDescriptor INSTANCE = new CustomFoldingSurroundDescriptor();
  public final static CustomFoldingRegionSurrounder[] SURROUNDERS;
  
  private final static String DEFAULT_DESC_TEXT = "Description";

  static {
    List<CustomFoldingRegionSurrounder> surrounderList = new ArrayList<CustomFoldingRegionSurrounder>();
    for (CustomFoldingProvider provider : CustomFoldingProvider.getAllProviders()) {
      surrounderList.add(new CustomFoldingRegionSurrounder(provider));
    }
    SURROUNDERS = surrounderList.toArray(new CustomFoldingRegionSurrounder[surrounderList.size()]);
  }

  @NotNull
  @Override
  public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    if (startOffset >= endOffset - 1) return PsiElement.EMPTY_ARRAY;
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(file.getLanguage());
    if (commenter == null || commenter.getLineCommentPrefix() == null) return PsiElement.EMPTY_ARRAY;
    PsiElement startElement = file.findElementAt(startOffset);
    if (startElement instanceof PsiWhiteSpace) startElement = startElement.getNextSibling();
    PsiElement endElement = file.findElementAt(endOffset - 1);
    if (endElement instanceof PsiWhiteSpace) endElement = endElement.getPrevSibling();
    if (startElement != null && endElement != null) {
      if (startElement.getTextRange().getStartOffset() > endElement.getTextRange().getStartOffset()) return PsiElement.EMPTY_ARRAY;
      startElement = findClosestParentAfterLineBreak(startElement);
      if (startElement != null) {
        endElement = findClosestParentBeforeLineBreak(endElement);
        if (endElement != null) {
          PsiElement commonParent = startElement.getParent();
          if (endElement.getParent() == commonParent) {
            if (startElement == endElement) return new PsiElement[] {startElement};
            return new PsiElement[] {startElement, endElement};
          }
        }
      }
    }
    return PsiElement.EMPTY_ARRAY;
  }
  
  @Nullable
  private static PsiElement findClosestParentAfterLineBreak(PsiElement element) {
    PsiElement parent = element;
    while (parent != null) {
      PsiElement prev = parent.getPrevSibling();
      if (prev instanceof PsiWhiteSpace && prev.textContains('\n')) return parent;
      parent = parent.getParent();
    }
    return null;
  }
  
  @Nullable
  private static PsiElement findClosestParentBeforeLineBreak(PsiElement element) {
    PsiElement parent = element;
    while (parent != null) {
      PsiElement next = parent.getNextSibling();
      if (next instanceof PsiWhiteSpace && next.textContains('\n')) return parent;
      parent = parent.getParent();
    }
    return null;
  }

  @NotNull
  @Override
  public Surrounder[] getSurrounders() {
    return SURROUNDERS;
  }

  @Override
  public boolean isExclusive() {
    return false;
  }

  private static class CustomFoldingRegionSurrounder implements Surrounder {

    private CustomFoldingProvider myProvider;

    public CustomFoldingRegionSurrounder(@NotNull CustomFoldingProvider provider) {
      myProvider = provider;
    }

    @Override
    public String getTemplateDescription() {
      return myProvider.getDescription();
    }

    @Override
    public boolean isApplicable(@NotNull PsiElement[] elements) {
      return true;
    }

    @Override
    public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements)
      throws IncorrectOperationException {
      if (elements.length == 0) return null;
      Language language = elements[0].getContainingFile().getLanguage();
      Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
      if (commenter == null) return null;
      String linePrefix = commenter.getLineCommentPrefix();
      if (linePrefix == null) return null;
      int prefixLength = linePrefix.length();
      int startOffset = elements[0].getTextRange().getStartOffset();
      int endOffset = elements[elements.length - 1].getTextRange().getEndOffset();
      int delta = 0;
      TextRange rangeToSelect = new TextRange(startOffset, startOffset);
      String startText = myProvider.getStartString();
      int descPos = startText.indexOf("?");
      if (descPos >= 0) {
        startText = startText.replace("?", DEFAULT_DESC_TEXT);
        rangeToSelect = new TextRange(startOffset + descPos, startOffset + descPos + DEFAULT_DESC_TEXT.length());
      }
      editor.getDocument().insertString(endOffset, "\n" + linePrefix + myProvider.getEndString());
      delta += myProvider.getEndString().length() + prefixLength;
      editor.getDocument().insertString(startOffset, linePrefix + startText + "\n");
      delta += startText.length() + prefixLength;
      rangeToSelect = rangeToSelect.shiftRight(prefixLength);
      TextRange formatRange = new TextRange(startOffset, endOffset).grown(delta);
      reformatFinalRange(project, elements[0].getContainingFile(), language, formatRange);
      return rangeToSelect;
    }
    
    private static void reformatFinalRange(@NotNull Project project, PsiFile file, Language language, TextRange range) {
      CommonCodeStyleSettings formatSettings = CodeStyleSettingsManager.getSettings(project).getCommonSettings(language);
      boolean keepAtFirstCol = formatSettings.KEEP_FIRST_COLUMN_COMMENT;
      formatSettings.KEEP_FIRST_COLUMN_COMMENT = false;
      CodeStyleManager.getInstance(project).reformatText(file, range.getStartOffset(), range.getEndOffset());
      formatSettings.KEEP_FIRST_COLUMN_COMMENT = keepAtFirstCol;
    }
  }
}
