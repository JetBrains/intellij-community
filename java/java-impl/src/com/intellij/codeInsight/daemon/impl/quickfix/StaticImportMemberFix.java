/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class StaticImportMemberFix<T extends PsiMember> implements IntentionAction, HintAction {
  // we keep max 2 candidates
  private List<T> candidates;

  @NotNull protected abstract String getBaseText();
  @NotNull protected abstract String getMemberPresentableText(T t);

  @Override
  @NotNull
  public String getText() {
    String text = getBaseText();
    if (candidates != null && candidates.size() == 1) {
      text += " '" + getMemberPresentableText(candidates.get(0)) + "'";
    }
    else {
      text += "...";
    }
    return text;
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
           && resolveRef() == null
           && file.getManager().isInProject(file)
           && !(candidates == null ? candidates = getMembersToImport(false, StaticMembersProcessor.SearchMode.MAX_2_MEMBERS) : candidates).isEmpty()
      ;
  }

  @NotNull protected abstract List<T> getMembersToImport(boolean applicableOnly, @NotNull StaticMembersProcessor.SearchMode mode);

  protected abstract boolean showMembersFromDefaultPackage();

  public static boolean isExcluded(PsiMember method) {
    String name = PsiUtil.getMemberQualifiedName(method);
    return name != null && JavaProjectCodeInsightSettings.getSettings(method.getProject()).isExcluded(name);
  }

  @NotNull protected abstract QuestionAction createQuestionAction(List<T> methodsToImport, @NotNull Project project, Editor editor);

  @Nullable protected abstract PsiElement getElement();
  @Nullable protected abstract PsiElement getQualifierExpression();
  @Nullable protected abstract PsiElement resolveRef();

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    ApplicationManager.getApplication().runWriteAction(() -> {
      final List<T> methodsToImport = getMembersToImport(false, StaticMembersProcessor.SearchMode.MAX_100_MEMBERS);
      if (methodsToImport.isEmpty()) return;
      createQuestionAction(methodsToImport, project, editor).execute();
    });
  }

  private ImportClassFixBase.Result doFix(Editor editor) {
    if (!CodeInsightSettings.getInstance().ADD_MEMBER_IMPORTS_ON_THE_FLY) {
      return ImportClassFixBase.Result.POPUP_NOT_SHOWN;
    }
    final List<T> candidates = getMembersToImport(true, StaticMembersProcessor.SearchMode.MAX_100_MEMBERS);
    if (candidates.isEmpty()) {
      return ImportClassFixBase.Result.POPUP_NOT_SHOWN;
    }

    final PsiElement element = getElement();
    if (element == null) {
      return ImportClassFixBase.Result.POPUP_NOT_SHOWN;
    }

    final QuestionAction action = createQuestionAction(candidates, element.getProject(), editor);
    /* PsiFile psiFile = element.getContainingFile();
   if (candidates.size() == 1 &&
        ImportClassFixBase.isAddUnambiguousImportsOnTheFlyEnabled(psiFile) &&
        (ApplicationManager.getApplication().isUnitTestMode() || DaemonListeners.canChangeFileSilently(psiFile)) &&
        !LaterInvocator.isInModalContext()) {
      CommandProcessor.getInstance().runUndoTransparentAction(() -> action.execute());
      return ImportClassFixBase.Result.CLASS_AUTO_IMPORTED;
    }
*/
    String hintText = ShowAutoImportPass.getMessage(candidates.size() > 1, getMemberPresentableText(candidates.get(0)));
    if (!ApplicationManager.getApplication().isUnitTestMode() && !HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) {
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
