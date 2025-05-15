// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

// will import elements of type T which are referenced by elements of type R (e.g., will import PsiMethods referenced by PsiMethodCallExpression)
@ApiStatus.Internal
public abstract class StaticImportMemberFix<T extends PsiMember, R extends PsiElement> implements HintAction {
  private final List<T> candidates;
  final SmartPsiElementPointer<R> myReferencePointer;
  private final long myPsiModificationCount;

  StaticImportMemberFix(@NotNull PsiFile psiFile, @NotNull R reference) {
    // there is a lot of PSI computations and resolve going on here,
    // so it must be created in a background thread under the read action to ensure no freezes are reported
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Project project = psiFile.getProject();
    myReferencePointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(reference);
    if (!PsiUtil.isAvailable(JavaFeature.STATIC_IMPORTS, psiFile)
        || !(psiFile instanceof PsiJavaFile)
        || getElement() == null
        || !reference.isValid()
        || getQualifierExpression() != null
        || !BaseIntentionAction.canModify(psiFile)
        || resolveRef() != null) {
      candidates = Collections.emptyList();
    }
    else {
      // search for suitable candidates here, in the background thread
      StaticMembersProcessor.MembersToImport<T> membersToImport = getMembersToImport(100);
      List<T> candidatesToImport = membersToImport.applicable().isEmpty() ? membersToImport.all() : membersToImport.applicable();
      candidates = ContainerUtil.filter(candidatesToImport, candidate -> isValidCandidate(psiFile, candidate));
    }
    myPsiModificationCount = PsiModificationTracker.getInstance(project).getModificationCount();
  }

  private static boolean isValidCandidate(PsiFile psiFile, PsiMember candidate){
    if (!candidate.isValid()) return false;
    if (PsiUtil.isMemberAccessibleAt(candidate, psiFile)) return true;
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(candidate);
    if (virtualFile == null) return false;
    return ProjectFileIndex.getInstance(psiFile.getProject()).isInContent(virtualFile);
  }

  @NotNull
  IntentionPreviewInfo generatePreview(@NotNull PsiFile psiFile, @NotNull BiConsumer<? super PsiElement, ? super T> consumer) {
    PsiElement copy = PsiTreeUtil.findSameElementInCopy(getElement(), psiFile);
    if (copy == null) return IntentionPreviewInfo.EMPTY;
    if (candidates.isEmpty()) return IntentionPreviewInfo.EMPTY;
    T element = candidates.get(0);
    PsiClass containingClass = element.getContainingClass();
    if (containingClass == null) return IntentionPreviewInfo.EMPTY;
    consumer.accept(copy, element);
    return IntentionPreviewInfo.DIFF;
  }

  protected abstract @NotNull @IntentionName String getBaseText();

  protected abstract @NotNull @NlsSafe String getMemberPresentableText(@NotNull T t);

  protected abstract @NotNull @NlsSafe String getMemberKindPresentableText();

  @Override
  public @NotNull String getText() {
    return getBaseText() + (candidates == null || candidates.size() != 1 ? "..." : " '" + getMemberPresentableText(candidates.get(0)) + "'");
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return !isPsiModificationStampChanged(project) && !candidates.isEmpty();
  }

  private boolean isPsiModificationStampChanged(@NotNull Project project) {
    long currentPsiModificationCount = PsiModificationTracker.getInstance(project).getModificationCount();
    return currentPsiModificationCount != myPsiModificationCount;
  }

  abstract @NotNull StaticMembersProcessor.MembersToImport<T> getMembersToImport(int maxResults);

  abstract boolean toAddStaticImports();

  protected abstract @NotNull QuestionAction createQuestionAction(@NotNull List<? extends T> methodsToImport, @NotNull Project project, Editor editor);

  @Nullable
  PsiElement getElement() {
    return myReferencePointer.getElement();
  }

  protected abstract @Nullable PsiElement getQualifierExpression();

  protected abstract @Nullable PsiElement resolveRef();

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!FileModificationService.getInstance().prepareFileForWrite(psiFile)
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
      String hintText =
        ShowAutoImportPass.getMessage(candidates.size() > 1, getMemberKindPresentableText(), getMemberPresentableText(firstCandidate));
      HintManager.getInstance().showQuestionHint(editor, hintText,
                                                 textRange.getStartOffset(),
                                                 textRange.getEndOffset(), action);
      return true;
    }

    return false;
  }
}
