/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.BaseRefactoringIntentionAction;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: Sep 6, 2010
 */
public class ChangeSignatureDetectorAction extends BaseRefactoringIntentionAction {
  public static final String CHANGE_SIGNATURE = "Apply signature change";
  public static final String NEW_NAME = "Apply new name";

  private String myAcceptText;

  @NotNull
  @Override
  public String getText() {
    final String text = myAcceptText;
    return text != null ? text : CHANGE_SIGNATURE;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return CHANGE_SIGNATURE;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    myAcceptText = null;
    final LanguageChangeSignatureDetector detector = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(element.getLanguage());
    if (detector != null) {
      ChangeSignatureGestureDetector signatureGestureDetector = ChangeSignatureGestureDetector.getInstance(project);
      PsiFile containingFile = element.getContainingFile();
      ChangeInfo changeInfo = signatureGestureDetector.getChangeInfo(containingFile);
      ChangeInfo initialChangeInfo = signatureGestureDetector.getInitialChangeInfo(containingFile);
      if (changeInfo != null && detector.isChangeSignatureAvailableOnElement(element, initialChangeInfo)) {
        myAcceptText = changeInfo instanceof RenameChangeInfo ? NEW_NAME : CHANGE_SIGNATURE;
      }
    }
    return myAcceptText != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    ChangeSignatureGestureDetector.getInstance(project).changeSignature(element.getContainingFile(), true);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
