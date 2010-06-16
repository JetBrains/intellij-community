/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageSurrounders;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class SurroundWithHandler implements CodeInsightActionHandler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler");
  private static final String CHOOSER_TITLE = CodeInsightBundle.message("surround.with.chooser.title");

  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull PsiFile file){
    invoke(project, editor, file, null);
  }

  public boolean startInWriteAction() {
    return true;
  }

  public static void invoke(final Project project, final Editor editor, PsiFile file, Surrounder surrounder){
    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
    }
    int startOffset = editor.getSelectionModel().getSelectionStart();
    int endOffset = editor.getSelectionModel().getSelectionEnd();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement element1 = file.findElementAt(startOffset);
    PsiElement element2 = file.findElementAt(endOffset - 1);

    if (element1 == null || element2 == null) return;

    TextRange textRange = new TextRange(startOffset, endOffset);
    for(SurroundWithRangeAdjuster adjuster: Extensions.getExtensions(SurroundWithRangeAdjuster.EP_NAME)) {
      textRange = adjuster.adjustSurroundWithRange(file, textRange);
      if (textRange == null) return;
    }
    startOffset = textRange.getStartOffset();
    endOffset = textRange.getEndOffset();
    element1 = file.findElementAt(startOffset);

    final Language baseLanguage = file.getViewProvider().getBaseLanguage();
    final Language l = element1.getParent().getLanguage();
    final Set<SurroundDescriptor> surroundDescriptors = new HashSet<SurroundDescriptor>();

    surroundDescriptors.addAll(LanguageSurrounders.INSTANCE.allForLanguage(l));
    if (l != baseLanguage) surroundDescriptors.addAll(LanguageSurrounders.INSTANCE.allForLanguage(baseLanguage));

    for (SurroundDescriptor descriptor : surroundDescriptors) {
      final PsiElement[] elements = descriptor.getElementsToSurround(file, startOffset, endOffset);
      if (elements.length > 0) {
        if (surrounder == null) { //production
          PopupActionChooser popupActionChooser = new PopupActionChooser(CHOOSER_TITLE);
          popupActionChooser.invoke(project, editor, file, descriptor.getSurrounders(), elements);
          if (popupActionChooser.isHasEnabledSurrounders()) return;
        }
        else {
          doSurround(project, editor, surrounder, elements);
          return;
        }
      }
    }

    if (surrounder == null) { //if only templates are available
      PopupActionChooser popupActionChooser = new PopupActionChooser(CHOOSER_TITLE);
      popupActionChooser.invoke(project, editor, file, new Surrounder[0], new PsiElement[0]);
    }
  }

  static void doSurround(final Project project, final Editor editor, final Surrounder surrounder, final PsiElement[] elements) {
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)){
      return;
    }

    try {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      int col = editor.getCaretModel().getLogicalPosition().column;
      int line = editor.getCaretModel().getLogicalPosition().line;
      LogicalPosition pos = new LogicalPosition(0, 0);
      editor.getCaretModel().moveToLogicalPosition(pos);
      TextRange range = surrounder.surroundElements(project, editor, elements);
      if (TemplateManager.getInstance(project).getActiveTemplate(editor) == null) {
        LogicalPosition pos1 = new LogicalPosition(line, col);
        editor.getCaretModel().moveToLogicalPosition(pos1);
      }
      if (range != null) {
        int offset = range.getStartOffset();
        editor.getCaretModel().moveToOffset(offset);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
