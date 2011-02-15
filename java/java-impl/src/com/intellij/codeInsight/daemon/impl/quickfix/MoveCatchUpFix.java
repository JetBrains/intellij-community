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

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class MoveCatchUpFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.DeleteCatchFix");

  private final PsiCatchSection myCatchSection;
  private final PsiCatchSection myMoveBeforeSection;

    public MoveCatchUpFix(PsiCatchSection catchSection, PsiCatchSection moveBeforeSection) {
    this.myCatchSection = catchSection;
        myMoveBeforeSection = moveBeforeSection;
    }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("move.catch.up.text",
                                  HighlightUtil.formatType(myCatchSection.getCatchType()),
                                  HighlightUtil.formatType(myMoveBeforeSection.getCatchType()));
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("move.catch.up.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myCatchSection != null
           && myCatchSection.isValid()
           && myCatchSection.getManager().isInProject(myCatchSection)
           && myMoveBeforeSection != null
           && myMoveBeforeSection.isValid()
           && myCatchSection.getCatchType() != null
           && PsiUtil.resolveClassInType(myCatchSection.getCatchType()) != null
           && myMoveBeforeSection.getCatchType() != null
           && PsiUtil.resolveClassInType(myMoveBeforeSection.getCatchType()) != null
           && !myCatchSection.getManager().areElementsEquivalent(
                  PsiUtil.resolveClassInType(myCatchSection.getCatchType()),
                  PsiUtil.resolveClassInType(myMoveBeforeSection.getCatchType()));
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myCatchSection.getContainingFile())) return;
    try {
      PsiTryStatement statement = myCatchSection.getTryStatement();
      statement.addBefore(myCatchSection, myMoveBeforeSection);
      myCatchSection.delete();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
