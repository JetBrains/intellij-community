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

package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Roman.Chernyatchik
 * @date Aug 13, 2009
 */
public abstract class AbstractSuppressByNoInspectionCommentFix extends SuppressIntentionAction {
  protected final String myID;
  private final boolean myReplaceOtherSuppressionIds;

  @Nullable
  protected abstract PsiElement getContainer(final PsiElement context);

  /**
   * @param ID Inspection ID
   * @param replaceOtherSuppressionIds Merge suppression policy. If false new tool id will be append to the end
   * otherwize replace other ids
   */
  public AbstractSuppressByNoInspectionCommentFix(final String ID, final boolean replaceOtherSuppressionIds) {
    myID = ID;
    myReplaceOtherSuppressionIds = replaceOtherSuppressionIds;
  }

  protected final void replaceSuppressionComment(@NotNull final PsiElement comment,
                                                 @NotNull final String oldSuppressionCommentText) {
    final String prefix = getLineCommentPrefix(comment);
    assert prefix != null && oldSuppressionCommentText.startsWith(prefix) : "Unexpected suppression comment " + oldSuppressionCommentText;

    final PsiParserFacade parserFacade = PsiParserFacade.SERVICE.getInstance(comment.getProject());

    // append new suppression tool id or replace
    final String newText = myReplaceOtherSuppressionIds
                           ? SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME + " " + myID
                           : oldSuppressionCommentText.substring(prefix.length()) + "," + myID;

    final PsiComment newComment =
      parserFacade.createLineCommentFromText((LanguageFileType)comment.getContainingFile().getFileType(), newText);
    comment.replace(newComment);
  }

  @Nullable
  protected String getLineCommentPrefix(final PsiElement comment) {
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(comment.getLanguage());
    assert commenter != null;

    return commenter.getLineCommentPrefix();
  }

  protected void createSuppression(final Project project,
                                 final Editor editor,
                                 final PsiElement element,
                                 final PsiElement container) throws IncorrectOperationException {
    final PsiParserFacade parserFacade = PsiParserFacade.SERVICE.getInstance(project);
    final String text = SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME + " " + myID;
    PsiComment comment = parserFacade.createLineOrBlockCommentFromText(element.getLanguage(), text);
    container.getParent().addBefore(comment, container);
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement context) {
    return context.getManager().isInProject(context) && getContainer(context) != null;
  }

  public void invoke(final Project project, @Nullable Editor editor, final PsiElement element) throws IncorrectOperationException {
    PsiElement container = getContainer(element);
    if (container == null) return;

    if (!CodeInsightUtilBase.preparePsiElementForWrite(container)) return;

    final List<? extends PsiElement> comments = getCommentsFor(container);
    if (comments != null) {
      for (PsiElement comment : comments) {
        if (comment instanceof PsiComment) {
          String text = comment.getText();
          final String lineCommentPrefix = getLineCommentPrefix(comment);
          assert lineCommentPrefix != null;

          if (text.startsWith(lineCommentPrefix + SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME)) {
            replaceSuppressionComment(comment, text);
            return;
          }
        }
      }
    }

    boolean caretWasBeforeStatement = editor != null && editor.getCaretModel().getOffset() == container.getTextRange().getStartOffset();
    createSuppression(project, editor, element, container);

    if (caretWasBeforeStatement) {
      editor.getCaretModel().moveToOffset(container.getTextRange().getStartOffset());
    }
    UndoUtil.markPsiFileForUndo(element.getContainingFile());
  }

  @Nullable
  protected List<? extends PsiElement> getCommentsFor(@NotNull final PsiElement container) {
    final PsiElement prev = PsiTreeUtil.skipSiblingsBackward(container, PsiWhiteSpace.class);
    if (prev == null) {
      return null;
    }
    return Collections.singletonList(prev);
  }


  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("suppress.inspection.family");
  }

  public boolean startInWriteAction() {
    return true;
  }
}
