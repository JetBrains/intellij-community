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
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MakeClassInterfaceFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MakeClassInterfaceFix");

  private final boolean myMakeInterface;
  private final String myName;

  public MakeClassInterfaceFix(PsiClass aClass, final boolean makeInterface) {
    super(aClass);
    myMakeInterface = makeInterface;
    myName = aClass.getName();
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message(myMakeInterface? "make.class.an.interface.text":"make.interface.an.class.text", myName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("make.class.an.interface.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiClass myClass = (PsiClass)startElement;

    return myClass.getManager().isInProject(myClass);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiClass myClass = (PsiClass)startElement;
    try {
      final PsiReferenceList extendsList = myMakeInterface? myClass.getExtendsList() : myClass.getImplementsList();
      final PsiReferenceList implementsList = myMakeInterface? myClass.getImplementsList() : myClass.getExtendsList();
      if (extendsList != null) {
        for (PsiJavaCodeReferenceElement referenceElement : extendsList.getReferenceElements()) {
          referenceElement.delete();
        }
        if (implementsList != null) {
          for (PsiJavaCodeReferenceElement referenceElement : implementsList.getReferenceElements()) {
            extendsList.addAfter(referenceElement, null);
            referenceElement.delete();
          }
        }
      }
      convertPsiClass(myClass, myMakeInterface);
      UndoUtil.markPsiFileForUndo(file);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void convertPsiClass(PsiClass aClass, final boolean makeInterface) throws IncorrectOperationException {
    final IElementType lookFor = makeInterface? JavaTokenType.CLASS_KEYWORD : JavaTokenType.INTERFACE_KEYWORD;
    final PsiKeyword replaceWith = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createKeyword(makeInterface? PsiKeyword.INTERFACE : PsiKeyword.CLASS);
    for (PsiElement psiElement : aClass.getChildren()) {
      if (psiElement instanceof PsiKeyword) {
        final PsiKeyword psiKeyword = (PsiKeyword)psiElement;
        if (psiKeyword.getTokenType() == lookFor) {
          psiKeyword.replace(replaceWith);
          break;
        }
      }
    }
  }
}
