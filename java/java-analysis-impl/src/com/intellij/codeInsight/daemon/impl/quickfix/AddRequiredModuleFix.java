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

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
public class AddRequiredModuleFix implements IntentionAction {
  private final SmartPsiElementPointer<PsiJavaModule> myModulePointer;
  private final String myRequiredName;

  public AddRequiredModuleFix(PsiJavaModule module, String requiredName) {
    myModulePointer = SmartPointerManager.getInstance(module.getProject()).createSmartPsiElementPointer(module);
    myRequiredName = requiredName;
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("module.info.add.requires.name", myRequiredName);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("module.info.add.requires.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!PsiUtil.isLanguageLevel9OrHigher(file)) return false;
    PsiJavaModule module = myModulePointer.getElement();
    return module != null && module.isValid() && module.getManager().isInProject(module) && getLBrace(module) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiJavaModule module = myModulePointer.getElement();
    if (module == null) return;

    PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(project).getParserFacade();
    PsiJavaModule tempModule =
      parserFacade.createModuleFromText("module " + module.getModuleName() + " { requires " + myRequiredName + "; }");
    Iterable<PsiRequiresStatement> tempModuleRequires = tempModule.getRequires();
    PsiRequiresStatement requiresStatement = tempModuleRequires.iterator().next();

    PsiElement addingPlace = findAddingPlace(module);
    if (addingPlace != null) {
      addingPlace.getParent().addAfter(requiresStatement, addingPlace);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Nullable
  private static PsiElement findAddingPlace(@NotNull PsiJavaModule module) {
    PsiElement addingPlace = ContainerUtil.iterateAndGetLastItem(module.getRequires());
    return addingPlace != null ? addingPlace : getLBrace(module);
  }

  @Nullable
  private static PsiElement getLBrace(@NotNull PsiJavaModule module) {
    PsiJavaModuleReferenceElement nameElement = module.getNameElement();
    for (PsiElement element = nameElement.getNextSibling(); element != null; element = element.getNextSibling()) {
      if (PsiUtil.isJavaToken(element, JavaTokenType.LBRACE)) {
        return element;
      }
    }
    return null; // module-info is incomplete
  }
}
