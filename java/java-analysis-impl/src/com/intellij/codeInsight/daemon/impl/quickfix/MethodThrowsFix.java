/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class MethodThrowsFix extends LocalQuickFixOnPsiElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MethodThrowsFix");

  private final String myThrowsCanonicalText;
  private final boolean myShouldThrow;
  private final String myMethodName;

  public MethodThrowsFix(@NotNull PsiMethod method, @NotNull PsiClassType exceptionType, boolean shouldThrow, boolean showContainingClass) {
    super(method);
    myThrowsCanonicalText = exceptionType.getCanonicalText();
    myShouldThrow = shouldThrow;
    myMethodName = PsiFormatUtil.formatMethod(method,
                                              PsiSubstitutor.EMPTY,
                                              PsiFormatUtilBase.SHOW_NAME | (showContainingClass ? PsiFormatUtilBase.SHOW_CONTAINING_CLASS
                                                                                                   : 0),
                                              0);
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message(myShouldThrow ? "fix.throws.list.add.exception" : "fix.throws.list.remove.exception",
                                  StringUtil.getShortName(myThrowsCanonicalText),
                                  myMethodName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.throws.list.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return !(((PsiMethod)startElement).getThrowsList() instanceof PsiCompiledElement); // can happen in Kotlin
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    final PsiMethod myMethod = (PsiMethod)startElement;
    PsiJavaCodeReferenceElement[] referenceElements = myMethod.getThrowsList().getReferenceElements();
    try {
      boolean alreadyThrows = false;
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        if (referenceElement.getCanonicalText().equals(myThrowsCanonicalText)) {
          alreadyThrows = true;
          if (!myShouldThrow) {
            referenceElement.delete();
            break;
          }
        }
      }
      if (myShouldThrow && !alreadyThrows) {
        final PsiElementFactory factory = JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory();
        final PsiClassType type = (PsiClassType)factory.createTypeFromText(myThrowsCanonicalText, myMethod);
        PsiJavaCodeReferenceElement ref = factory.createReferenceElementByType(type);
        ref = (PsiJavaCodeReferenceElement)JavaCodeStyleManager.getInstance(project).shortenClassReferences(ref);
        myMethod.getThrowsList().add(ref);
      }
      UndoUtil.markPsiFileForUndo(file);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
