// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ContainerBasedSuppressQuickFix;
import com.intellij.codeInspection.InjectionAwareSuppressQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
 */
public abstract class AbstractBatchSuppressByNoInspectionCommentFix implements ContainerBasedSuppressQuickFix, InjectionAwareSuppressQuickFix, Iconable {
  protected final @NotNull String myID;
  private final boolean myReplaceOtherSuppressionIds;
  private ThreeState myShouldBeAppliedToInjectionHost = ThreeState.UNSURE;

  @Override
  public abstract @Nullable PsiElement getContainer(PsiElement context);

  /**
   * @param ID                         Inspection ID
   * @param replaceOtherSuppressionIds Merge suppression policy. If false new tool id will be appended to the end
   *                                   otherwise replace other ids
   */
  public AbstractBatchSuppressByNoInspectionCommentFix(@NotNull String ID, boolean replaceOtherSuppressionIds) {
    myID = ID;
    myReplaceOtherSuppressionIds = replaceOtherSuppressionIds;
  }

  @Override
  public void setShouldBeAppliedToInjectionHost(@NotNull ThreeState shouldBeAppliedToInjectionHost) {
    myShouldBeAppliedToInjectionHost = shouldBeAppliedToInjectionHost;
  }

  @Override
  public @NotNull ThreeState isShouldBeAppliedToInjectionHost() {
    return myShouldBeAppliedToInjectionHost;
  }

  @Override
  public @NotNull String getName() {
    return getText();
  }

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Ide.HectorOff;
  }

  private @IntentionName String myText = "";

  public @IntentionName @NotNull String getText() {
    return myText;
  }

  protected void setText(@IntentionName @NotNull String text) {
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
    return SuppressionUtil.ALL.equalsIgnoreCase(myID);
  }

  protected final void replaceSuppressionComment(@NotNull PsiElement comment) {
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
  protected @NotNull Language getCommentLanguage(@NotNull PsiElement element) {
    return element.getLanguage();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @NotNull PsiElement context) {
    return context.isValid() && getContainer(context) != null;
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!isAvailable(project, element)) return;
    PsiElement container = getContainer(element);
    PsiFile psiFile = element.getContainingFile();
    if (container == null) return;

    if (replaceSuppressionComments(container)) return;

    createSuppression(project, element, container);
    UndoUtil.markPsiFileForUndo(psiFile);
  }

  protected boolean replaceSuppressionComments(@NotNull PsiElement container) {
    List<? extends PsiElement> comments = getCommentsFor(container);
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

  protected @Nullable List<? extends PsiElement> getCommentsFor(@NotNull PsiElement container) {
    PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(container);
    if (prev == null) {
      return null;
    }
    return Collections.singletonList(prev);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    // Suppression actions have no preview
    return IntentionPreviewInfo.EMPTY;
  }

  @Override
  public @NotNull String getFamilyName() {
    String text = getText();
    return StringUtil.isEmpty(text) ? AnalysisBundle.message("suppress.inspection.family") : text;
  }
}
