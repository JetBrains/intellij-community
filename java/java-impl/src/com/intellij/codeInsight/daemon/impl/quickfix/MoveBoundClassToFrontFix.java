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
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveBoundClassToFrontFix extends ExtendsListFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MoveBoundClassToFrontFix");
  private final String myName;

  public MoveBoundClassToFrontFix(@NotNull PsiClass aClass, @NotNull PsiClassType classToExtendFrom) {
    super(aClass, classToExtendFrom, true);
    myName = QuickFixBundle.message("move.bound.class.to.front.fix.text",
                                    HighlightUtil.formatClass(myClassToExtendFrom),
                                    HighlightUtil.formatClass(aClass));
  }

  @Override
  @NotNull
  public String getText() {
    return myName;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("move.class.in.extend.list.family");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiClass myClass = (PsiClass)startElement;
    PsiReferenceList extendsList = myClass.getExtendsList();
    if (extendsList == null) return;
    try {
      modifyList(extendsList, false, -1);
      modifyList(extendsList, true, 0);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    UndoUtil.markPsiFileForUndo(file);
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiClass myClass = (PsiClass)startElement;
    return
      myClass.getManager().isInProject(myClass)
      && myClassToExtendFrom != null
      && myClassToExtendFrom.isValid()
    ;
  }
}
