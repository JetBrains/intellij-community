// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.HintAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

// will import elements of type T which are referenced by elements of type R (e.g., will import PsiMethods referenced by PsiMethodCallExpression)
abstract class StaticImportMemberFix<T extends PsiMember, R extends PsiElement> implements HintAction {
  private final List<T> candidates;
  final SmartPsiElementPointer<R> myReferencePointer;
  private final long myPsiModificationCount;

  StaticImportMemberFix(@NotNull PsiFile file, @NotNull R reference) {
    // there is a lot of PSI computations and resolve going on here,
    // so it must be created in a background thread under the read action to ensure no freezes are reported
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Project project = file.getProject();
    myReferencePointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(reference);
    if (!PsiUtil.isLanguageLevel5OrHigher(file)
        || !(file instanceof PsiJavaFile)
        || getElement() == null
        || !reference.isValid()
        || getQualifierExpression() != null
        || !BaseIntentionAction.canModify(file)
        || resolveRef() != null) {
      candidates = Collections.emptyList();
    }
    else {
      // search for suitable candidates here, in the background thread
      List<T> applicableCandidates = getMembersToImport(true, 100);
      List<T> candidatesToImport = applicableCandidates.isEmpty() ? getMembersToImport(false, 2) : applicableCandidates;
      candidates = ContainerUtil.filter(candidatesToImport, candidate -> isValidCandidate(file, candidate));
    }
    myPsiModificationCount = PsiModificationTracker.getInstance(project).getModificationCount();
  }

  private static boolean isValidCandidate(PsiFile file, PsiMember candidate){
    if (!candidate.isValid()) return false;
    if (PsiUtil.isMemberAccessibleAt(candidate, file)) return true;
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(candidate);
    if (virtualFile == null) return false;
    return ProjectFileIndex.getInstance(file.getProject()).isInContent(virtualFile);
  }

  @NotNull
  IntentionPreviewInfo generatePreview(@NotNull PsiFile file, @NotNull BiConsumer<? super PsiElement, ? super T> consumer) {
    PsiElement copy = PsiTreeUtil.findSameElementInCopy(getElement(), file);
    if (copy == null) return IntentionPreviewInfo.EMPTY;
    if (candidates.isEmpty()) return IntentionPreviewInfo.EMPTY;
    T element = candidates.get(0);
    PsiClass containingClass = element.getContainingClass();
    if (containingClass == null) return IntentionPreviewInfo.EMPTY;
    consumer.accept(copy, element);
    return IntentionPreviewInfo.DIFF;
  }

  @NotNull
  protected abstract @IntentionName String getBaseText();

  @NotNull
  protected abstract @NlsSafe String getMemberPresentableText(@NotNull T t);

  @Override
  @NotNull
  public String getText() {
    return getBaseText() + (candidates == null || candidates.size() != 1 ? "..." : " '" + getMemberPresentableText(candidates.get(0)) + "'");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return !isPsiModificationStampChanged(project) && !candidates.isEmpty();
  }

  private boolean isPsiModificationStampChanged(@NotNull Project project) {
    long currentPsiModificationCount = PsiModificationTracker.getInstance(project).getModificationCount();
    return currentPsiModificationCount != myPsiModificationCount;
  }

  @NotNull
  abstract List<T> getMembersToImport(boolean applicableOnly, int maxResults);

  abstract boolean toAddStaticImports();

  @NotNull
  protected abstract QuestionAction createQuestionAction(@NotNull List<? extends T> methodsToImport, @NotNull Project project, Editor editor);

  @Nullable
  PsiElement getElement() {
    return myReferencePointer.getElement();
  }

  @Nullable
  protected abstract PsiElement getQualifierExpression();

  @Nullable
  protected abstract PsiElement resolveRef();

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)
        || isPsiModificationStampChanged(project)
        || candidates.isEmpty()) {
      return;
    }
    ApplicationManager.getApplication().runWriteAction(() -> {
      createQuestionAction(candidates, project, editor).execute();
    });
  }


  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean showHint(@NotNull Editor editor) {
    PsiElement callExpression = getElement();
    if (callExpression == null || getQualifierExpression() != null) {
      return false;
    }
    if (!CodeInsightSettings.getInstance().ADD_MEMBER_IMPORTS_ON_THE_FLY) {
      return false;
    }
    if (candidates.isEmpty()) {
      return false;
    }

    T firstCandidate = candidates.get(0);
    PsiFile containingFile = callExpression.getContainingFile();
    if (containingFile == null || isPsiModificationStampChanged(containingFile.getProject())) {
      return false;
    }

    if ((!toAddStaticImports() ||
         candidates.size() != 1 ||
         !PsiTreeUtil.isAncestor(containingFile, firstCandidate, true))
        && !ApplicationManager.getApplication().isHeadlessEnvironment()
        && !HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) {
      TextRange textRange = callExpression.getTextRange();
      QuestionAction action = createQuestionAction(candidates, containingFile.getProject(), editor);
      String hintText = ShowAutoImportPass.getMessage(candidates.size() > 1, getMemberPresentableText(firstCandidate));
      HintManager.getInstance().showQuestionHint(editor, hintText,
                                                 textRange.getStartOffset(),
                                                 textRange.getEndOffset(), action);
      return true;
    }

    return false;
  }
}
