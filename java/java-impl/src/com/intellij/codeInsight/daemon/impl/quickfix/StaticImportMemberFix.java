// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.IntentionActionWithModCommandFallback;
import com.intellij.codeInsight.intention.impl.AddSingleMemberStaticImportAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.HintAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

// will import elements of type T which are referenced by elements of type R (e.g., will import PsiMethods referenced by PsiMethodCallExpression)
@ApiStatus.Internal
public abstract class StaticImportMemberFix<T extends PsiMember, R extends PsiElement>
  implements HintAction, IntentionActionWithModCommandFallback {
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

  private static boolean isValidCandidate(@NotNull PsiFile psiFile, @NotNull PsiMember candidate){
    if (!candidate.isValid()) return false;
    if (PsiUtil.isMemberAccessibleAt(candidate, psiFile)) return true;
    PsiImplicitClass possibleImplicitClass = PsiTreeUtil.getParentOfType(candidate, PsiImplicitClass.class);
    if (possibleImplicitClass != null) {
      if (!psiFile.equals(candidate.getContainingFile())) return false;
    }
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(candidate);
    if (virtualFile == null) return false;
    return ProjectFileIndex.getInstance(psiFile.getProject()).isInContent(virtualFile);
  }

  protected abstract @NotNull @IntentionName String getBaseText();

  protected abstract @NotNull @NlsSafe String getMemberPresentableText(@NotNull T t);

  protected abstract @NotNull @NlsSafe String getMemberKindPresentableText();

  @Override
  public @NotNull String getText() {
    return getBaseText() + (candidates == null || candidates.size() != 1 ? "..." : " '" + getMemberPresentableText(candidates.getFirst()) + "'");
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

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    R copy = PsiTreeUtil.findSameElementInCopy(getElement(), psiFile);
    if (copy == null) return IntentionPreviewInfo.EMPTY;
    if (candidates.isEmpty()) return IntentionPreviewInfo.EMPTY;
    T element = candidates.getFirst();
    PsiClass containingClass = element.getContainingClass();
    if (containingClass == null) return IntentionPreviewInfo.EMPTY;
    R ref = myReferencePointer.getElement();
    if (ref == null) return IntentionPreviewInfo.EMPTY;
    performImport(element, copy);
    return IntentionPreviewInfo.DIFF;
  }

  abstract @NotNull StaticMembersProcessor.MembersToImport<T> getMembersToImport(int maxResults);

  abstract boolean toAddStaticImports();

  protected abstract @Nls @NotNull String getSelectorTitle();

  @NotNull
  private QuestionAction createQuestionAction(@NotNull List<? extends T> membersToImport, @NotNull Project project, Editor editor) {
    return new StaticImportMemberQuestionAction<T>(project, editor, membersToImport, myReferencePointer, getSelectorTitle()) {
      @Override
      protected void doImport(@NotNull T toImport) {
        R ref = myReferencePointer.getElement();
        if (ref == null) return;
        if (!FileModificationService.getInstance().preparePsiElementForWrite(ref)) return;
        Project project = toImport.getProject();
        WriteCommandAction.runWriteCommandAction(project, getBaseText(), null, () -> performImport(toImport, ref));
      }
    };
  }

  /**
   * @param toImport member to import
   * @param ref reference
   */
  protected void performImport(@NotNull T toImport, @NotNull R ref) {
    AddSingleMemberStaticImportAction.bindAllClassRefs(ref.getContainingFile(), toImport, toImport.getName(), toImport.getContainingClass());
  }

  @Nullable R getElement() {
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

    T firstCandidate = candidates.getFirst();
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

  @Override
  public @NotNull ModCommandAction getFallbackModCommandAction() {
    return new StaticImportMemberModCommandAction();
  }

  private class StaticImportMemberModCommandAction implements ModCommandAction {
    @Override
    public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
      if (isPsiModificationStampChanged(context.project()) || candidates.isEmpty()) return null;
      return Presentation.of(getText());
    }

    @Override
    public @NotNull ModCommand perform(@NotNull ActionContext context) {
      R ref = myReferencePointer.getElement();
      return ModCommand.chooseAction(
        getSelectorTitle(),
        ContainerUtil.map(candidates, c -> new StaticImportMemberSingleAction(ref, c)));
    }

    @Override
    public @NotNull String getFamilyName() {
      return StaticImportMemberFix.this.getFamilyName();
    }
  }

  private class StaticImportMemberSingleAction extends PsiUpdateModCommandAction<R> {
    private final T myMember;

    private StaticImportMemberSingleAction(R ref, T member) {
      super(ref);
      myMember = member;
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull R element) {
      if (!myMember.isValid()) return null;
      PsiClass aClass = Objects.requireNonNull(myMember.getContainingClass());
      String presentation = ClassPresentationUtil.getNameForClass(aClass, false) + "." + myMember.getName();
      return Presentation.of(presentation);
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull R element, @NotNull ModPsiUpdater updater) {
      performImport(myMember, element);
    }

    @Override
    public @NotNull String getFamilyName() {
      return StaticImportMemberFix.this.getFamilyName();
    }
  }
}
