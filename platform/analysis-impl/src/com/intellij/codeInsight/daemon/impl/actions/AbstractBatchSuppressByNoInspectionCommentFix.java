/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.*;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Roman.Chernyatchik
 * @date Aug 13, 2009
 */
public abstract class AbstractBatchSuppressByNoInspectionCommentFix implements ContainerBasedSuppressQuickFix, InjectionAwareSuppressQuickFix, Iconable {
  @NotNull protected final String myID;
  private final boolean myReplaceOtherSuppressionIds;
  private ThreeState myShouldBeAppliedToInjectionHost = ThreeState.UNSURE;

  @Override
  @Nullable
  public abstract PsiElement getContainer(final PsiElement context);

  /**
   * @param ID                         Inspection ID
   * @param replaceOtherSuppressionIds Merge suppression policy. If false new tool id will be append to the end
   *                                   otherwise replace other ids
   */
  public AbstractBatchSuppressByNoInspectionCommentFix(@NotNull String ID, final boolean replaceOtherSuppressionIds) {
    myID = ID;
    myReplaceOtherSuppressionIds = replaceOtherSuppressionIds;
  }

  public void setShouldBeAppliedToInjectionHost(@NotNull ThreeState shouldBeAppliedToInjectionHost) {
    myShouldBeAppliedToInjectionHost = shouldBeAppliedToInjectionHost;
  }

  @NotNull
  @Override
  public ThreeState isShouldBeAppliedToInjectionHost() {
    return myShouldBeAppliedToInjectionHost;
  }

  @NotNull
  @Override
  public String getName() {
    return getText();
  }

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.General.InspectionsOff;
  }

  private String myText = "";
  @NotNull
  public String getText() {
    return myText;
  }

  protected void setText(@NotNull String text) {
    myText = text;
  }

  @Override
  public String toString() {
    return getText();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getStartElement();
    if (element == null) return;
    invoke(project, element);
  }

  @Override
  public boolean isSuppressAll() {
    return SuppressionUtil.ALL.equals(myID);
  }

  protected final void replaceSuppressionComment(@NotNull final PsiElement comment) {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(comment)) return;
    WriteAction.run(() -> SuppressionUtil.replaceSuppressionComment(comment, myID, myReplaceOtherSuppressionIds, getCommentLanguage(comment)));
  }

  protected void createSuppression(@NotNull Project project,
                                   @NotNull PsiElement element,
                                   @NotNull PsiElement container) throws IncorrectOperationException {
    SuppressionUtil.createSuppression(project, container, myID, getCommentLanguage(element));
  }

  /**
   * @param element quickfix target or existing comment element
   * @return language that will be used for comment creating.
   * In common case language will be the same as language of quickfix target
   */
  @NotNull
  protected Language getCommentLanguage(@NotNull PsiElement element) {
    return element.getLanguage();
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, @NotNull final PsiElement context) {
    return context.isValid() && getContainer(context) != null;
  }

  public void invoke(@NotNull final Project project, @NotNull final PsiElement element) throws IncorrectOperationException {
    if (!isAvailable(project, element)) return;
    PsiElement container = getContainer(element);
    if (container == null) return;

    if (replaceSuppressionComments(container)) return;

    createSuppression(project, element, container);
    UndoUtil.markPsiFileForUndo(element.getContainingFile());
  }

  protected boolean replaceSuppressionComments(PsiElement container) {
    final List<? extends PsiElement> comments = getCommentsFor(container);
    if (comments != null) {
      for (PsiElement comment : comments) {
        if (comment instanceof PsiComment && SuppressionUtil.isSuppressionComment(comment)) {
          replaceSuppressionComment(comment);
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  protected List<? extends PsiElement> getCommentsFor(@NotNull final PsiElement container) {
    final PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(container);
    if (prev == null) {
      return null;
    }
    return Collections.singletonList(prev);
  }


  @Override
  @NotNull
  public String getFamilyName() {
    final String text = getText();
    return StringUtil.isEmpty(text) ? InspectionsBundle.message("suppress.inspection.family") : text;
  }
}
