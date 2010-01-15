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

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import org.jetbrains.annotations.NotNull;

public class MethodReturnFix extends IntentionAndQuickFixAction {
  private final PsiMethod myMethod;
  private final PsiType myReturnType;
  private final boolean myFixWholeHierarchy;

  public MethodReturnFix(PsiMethod method, PsiType toReturn, boolean fixWholeHierarchy) {
    myMethod = method;
    myReturnType = toReturn;
    myFixWholeHierarchy = fixWholeHierarchy;
  }

  @NotNull
  public String getName() {
    return QuickFixBundle.message("fix.return.type.text",
                                  myMethod.getName(),
                                  myReturnType.getCanonicalText());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.return.type.family");
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myMethod != null
        && myMethod.isValid()
        && myMethod.getManager().isInProject(myMethod)
        && myReturnType != null
        && myReturnType.isValid()
        && !TypeConversionUtil.isNullType(myReturnType)
        && myMethod.getReturnType() != null
        && !Comparing.equal(myReturnType, myMethod.getReturnType());
  }

  public void applyFix(final Project project, final PsiFile file, final Editor editor) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myMethod.getContainingFile())) return;
    PsiMethod method = myMethod;
    if (myFixWholeHierarchy) {
      final PsiMethod superMethod = myMethod.findDeepestSuperMethod();
      if (superMethod != null) {
        final PsiType superReturnType = superMethod.getReturnType();
        if (superReturnType != null && !Comparing.equal(myReturnType, superReturnType)) {
          method = SuperMethodWarningUtil.checkSuperMethod(myMethod, RefactoringBundle.message("to.refactor"));
          if (method == null) return;
        }
      }
    }
    if (!CodeInsightUtilBase.prepareFileForWrite(method.getContainingFile())) return;
    ChangeSignatureProcessor processor = new ChangeSignatureProcessor(myMethod.getProject(),
                                                                      method,
        false, null,
        method.getName(),
        myReturnType,
        RemoveUnusedParameterFix.getNewParametersInfo(method, null));
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      processor.run();
    }
    else {
      processor.run();
    }
    if (method.getContainingFile() != file) {
      UndoUtil.markPsiFileForUndo(file);
    }
  }

}
