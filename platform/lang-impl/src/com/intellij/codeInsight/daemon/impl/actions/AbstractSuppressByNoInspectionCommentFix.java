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

package com.intellij.codeInsight.daemon.impl.actions;

import com.google.common.base.Strings;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
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
   * @param ID                         Inspection ID
   * @param replaceOtherSuppressionIds Merge suppression policy. If false new tool id will be append to the end
   *                                   otherwise replace other ids
   */
  public AbstractSuppressByNoInspectionCommentFix(final String ID, final boolean replaceOtherSuppressionIds) {
    myID = ID;
    myReplaceOtherSuppressionIds = replaceOtherSuppressionIds;
  }

  protected final void replaceSuppressionComment(@NotNull final PsiElement comment) {
    final String oldSuppressionCommentText = comment.getText();
    final String lineCommentPrefix = getLineCommentPrefix(comment);
    Pair<String, String> blockPrefixSuffix = null;
    if (lineCommentPrefix == null) {
      blockPrefixSuffix = getBlockPrefixSuffixPair(comment);
    }
    assert (blockPrefixSuffix != null && oldSuppressionCommentText.startsWith(blockPrefixSuffix.first)) && oldSuppressionCommentText.endsWith(blockPrefixSuffix.second)
           || (lineCommentPrefix != null && oldSuppressionCommentText.startsWith(lineCommentPrefix))
      : "Unexpected suppression comment " + oldSuppressionCommentText;

    // append new suppression tool id or replace
    final String newText;
    if(myReplaceOtherSuppressionIds) {
      newText = SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME + " " + myID;
    } else {
      if (lineCommentPrefix != null) {
        newText = oldSuppressionCommentText.substring(lineCommentPrefix.length()) + "," + myID;
      } else {
        newText = oldSuppressionCommentText.substring(blockPrefixSuffix.first.length(),
                                                      oldSuppressionCommentText.length() - blockPrefixSuffix.second.length()) + "," + myID;
      }
    }

    PsiElement parent = comment.getParent();
    comment.replace(createComment(comment.getProject(), parent != null ? parent : comment, newText));
  }

  @Nullable
  private static String getLineCommentPrefix(@NotNull final PsiElement comment) {
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(comment.getLanguage());
    return commenter == null ? null : commenter.getLineCommentPrefix();
  }

  @Nullable
  private static Pair<String, String> getBlockPrefixSuffixPair(PsiElement comment) {
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(comment.getLanguage());
    if (commenter != null) {
      final String prefix = commenter.getBlockCommentPrefix();
      final String suffix = commenter.getBlockCommentSuffix();
      if (prefix != null || suffix != null) {
        return Pair.create(Strings.nullToEmpty(prefix), Strings.nullToEmpty(suffix));
      }
    }
    return null;
  }

  protected void createSuppression(final Project project,
                                 final Editor editor,
                                 final PsiElement element,
                                 final PsiElement container) throws IncorrectOperationException {
    final String text = SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME + " " + myID;
    PsiComment comment = createComment(project, element, text);
    container.getParent().addBefore(comment, container);
  }

  @NotNull
  protected PsiComment createComment(Project project, PsiElement element, String commentText) {
    final PsiParserFacade parserFacade = PsiParserFacade.SERVICE.getInstance(project);
    return parserFacade.createLineOrBlockCommentFromText(element.getLanguage(), commentText);
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement context) {
    return context.isValid() && context.getManager().isInProject(context) && getContainer(context) != null;
  }

  @Override
  public void invoke(@NotNull final Project project, @Nullable Editor editor, @NotNull final PsiElement element) throws IncorrectOperationException {
    PsiElement container = getContainer(element);
    if (container == null) return;

    if (!FileModificationService.getInstance().preparePsiElementForWrite(container)) return;

    final List<? extends PsiElement> comments = getCommentsFor(container);
    if (comments != null) {
      for (PsiElement comment : comments) {
        if (comment instanceof PsiComment && isSuppressionComment(comment)) {
          replaceSuppressionComment(comment);
          return;
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

  public static boolean isSuppressionComment(PsiElement comment) {
    final String prefix = getLineCommentPrefix(comment);
    final String commentText = comment.getText();
    if (prefix != null) {
      return commentText.startsWith(prefix + SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME);
    }
    final Pair<String, String> prefixSuffixPair = getBlockPrefixSuffixPair(comment);
    return prefixSuffixPair != null
           && commentText.startsWith(prefixSuffixPair.first + SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME)
           && commentText.endsWith(prefixSuffixPair.second);
  }

  @Nullable
  protected List<? extends PsiElement> getCommentsFor(@NotNull final PsiElement container) {
    final PsiElement prev = PsiTreeUtil.skipSiblingsBackward(container, PsiWhiteSpace.class);
    if (prev == null) {
      return null;
    }
    return Collections.singletonList(prev);
  }


  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("suppress.inspection.family");
  }
}
