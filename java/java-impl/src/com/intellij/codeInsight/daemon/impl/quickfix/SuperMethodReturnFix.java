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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import org.jetbrains.annotations.NotNull;

public class SuperMethodReturnFix implements IntentionAction {

  private final PsiType mySuperMethodType;
  private final PsiMethod mySuperMethod;

  public SuperMethodReturnFix(@NotNull PsiMethod superMethod, @NotNull PsiType superMethodType) {
    mySuperMethodType = superMethodType;
    mySuperMethod = superMethod;
  }

  @Override
  @NotNull
  public String getText() {
    String name = PsiFormatUtil.formatMethod(
            mySuperMethod,
            PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS,
            0
    );
    return QuickFixBundle.message("fix.super.method.return.type.text",
                                  name,
                                  JavaHighlightUtil.formatType(mySuperMethodType));
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.super.method.return.type.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return mySuperMethod.isValid() && BaseIntentionAction.canModify(mySuperMethod) && mySuperMethodType.isValid();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(mySuperMethod.getContainingFile())) return;
    var provider = JavaSpecialRefactoringProvider.getInstance();
    var processor = provider.getChangeSignatureProcessorWithCallback(
      project,
      mySuperMethod,
      false, null,
      mySuperMethod.getName(),
      mySuperMethodType,
      ParameterInfoImpl.fromMethod(mySuperMethod),
      true,
      null
    );
    processor.run();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
