// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.HintAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

// will import elements of type T which are referenced by elements of type R (e.g. will import PsiMethods referenced by PsiMethodCallExpression)
public abstract class StaticImportMemberFix<T extends PsiMember, R extends PsiElement> implements IntentionAction, HintAction {
  private final List<SmartPsiElementPointer<T>> myApplicableCandidates;
  private final List<T> candidates;
  protected final SmartPsiElementPointer<R> myRef;

  @SuppressWarnings("AbstractMethodCallInConstructor")
  StaticImportMemberFix(@NotNull PsiFile file, @NotNull R reference) {
    myRef = SmartPointerManager.getInstance(file.getProject()).createSmartPsiElementPointer(reference);
    // search for suitable candidates here, in the background thread
    List<T> applicableCandidates = getMembersToImport(true, StaticMembersProcessor.SearchMode.MAX_100_MEMBERS);
    candidates = !applicableCandidates.isEmpty() ? applicableCandidates 
                                                 : getMembersToImport(false, StaticMembersProcessor.SearchMode.MAX_2_MEMBERS);

    myApplicableCandidates = ContainerUtil.map(applicableCandidates, SmartPointerManager::createPointer);
  }

  @NotNull
  protected IntentionPreviewInfo generatePreview(@NotNull PsiFile file, BiConsumer<PsiElement, T> consumer) {
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
    return getBaseText() +
           (candidates == null || candidates.size() != 1 ? "..." :
            " '" + getMemberPresentableText(candidates.get(0)) + "'");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file)
           && file instanceof PsiJavaFile
           && getElement() != null
           && getElement().isValid()
           && getQualifierExpression() == null
           && BaseIntentionAction.canModify(file)
           && !candidates.isEmpty()
           && ContainerUtil.all(candidates, PsiElement::isValid)
           && resolveRef() == null
      ;
  }

  @NotNull
  protected abstract List<T> getMembersToImport(boolean applicableOnly, @NotNull StaticMembersProcessor.SearchMode mode);

  protected abstract boolean toAddStaticImports();

  public static boolean isExcluded(@NotNull PsiMember method) {
    String name = PsiUtil.getMemberQualifiedName(method);
    return name != null && JavaProjectCodeInsightSettings.getSettings(method.getProject()).isExcluded(name);
  }

  @NotNull
  protected abstract QuestionAction createQuestionAction(@NotNull List<? extends T> methodsToImport, @NotNull Project project, Editor editor);

  @Nullable
  protected abstract PsiElement getElement();

  @Nullable
  protected abstract PsiElement getQualifierExpression();

  @Nullable
  protected abstract PsiElement resolveRef();

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    ApplicationManager.getApplication().runWriteAction(() -> {
      List<T> applicableCandidates = ContainerUtil.mapNotNull(myApplicableCandidates, SmartPsiElementPointer::getElement);
      final List<T> methodsToImport = !applicableCandidates.isEmpty() ? applicableCandidates 
                                                                      : getMembersToImport(false, StaticMembersProcessor.SearchMode.MAX_100_MEMBERS);
      if (methodsToImport.isEmpty()) return;
      createQuestionAction(methodsToImport, project, editor).execute();
    });
  }

  @NotNull
  private ImportClassFixBase.Result doFix(@NotNull Editor editor) {
    if (!CodeInsightSettings.getInstance().ADD_MEMBER_IMPORTS_ON_THE_FLY) {
      return ImportClassFixBase.Result.POPUP_NOT_SHOWN;
    }
    List<T> candidates = ContainerUtil.mapNotNull(myApplicableCandidates, SmartPsiElementPointer::getElement);
    if (candidates.isEmpty()) {
      return ImportClassFixBase.Result.POPUP_NOT_SHOWN;
    }

    final PsiElement element = getElement();
    if (element == null) {
      return ImportClassFixBase.Result.POPUP_NOT_SHOWN;
    }

    if (toAddStaticImports() &&
        candidates.size() == 1 &&
        PsiTreeUtil.isAncestor(element.getContainingFile(), candidates.get(0), true)) {
      return ImportClassFixBase.Result.POPUP_NOT_SHOWN;
    }

    final QuestionAction action = createQuestionAction(candidates, element.getProject(), editor);
    String hintText = ShowAutoImportPass.getMessage(candidates.size() > 1, getMemberPresentableText(candidates.get(0)));
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()
        && !HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) {
      final TextRange textRange = element.getTextRange();
      HintManager.getInstance().showQuestionHint(editor, hintText,
                                                 textRange.getStartOffset(),
                                                 textRange.getEndOffset(), action);
    }
    return ImportClassFixBase.Result.POPUP_SHOWN;
  }

  

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean showHint(@NotNull Editor editor) {
    final PsiElement callExpression = getElement();
    if (callExpression == null || 
        getQualifierExpression() != null) {
      return false;
    }
    ImportClassFixBase.Result result = doFix(editor);
    return result == ImportClassFixBase.Result.POPUP_SHOWN || result == ImportClassFixBase.Result.CLASS_AUTO_IMPORTED;
  }
}
