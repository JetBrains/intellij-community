/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.editorActions.FixDocCommentAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class AddJavadocIntention extends PsiElementBaseIntentionAction implements LowPriorityAction {
  @Override
  public void invoke(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) throws IncorrectOperationException {
    final PsiDocCommentOwner docCommentOwner = (PsiDocCommentOwner)element.getParent();
    FixDocCommentAction.generateOrFixComment(docCommentOwner, project, editor);
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
    if (!(element instanceof PsiIdentifier)) {
      return false;
    }
    final PsiElement docCommentOwner = element.getParent();
    return docCommentOwner instanceof PsiDocCommentOwner &&
           !(docCommentOwner instanceof PsiAnonymousClass) && ((PsiDocCommentOwner)docCommentOwner).getDocComment() == null;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Add Javadoc";
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }
}
