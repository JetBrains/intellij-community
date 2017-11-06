// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
public class AddRequiredModuleFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String myRequiredName;

  public AddRequiredModuleFix(PsiJavaModule module, String requiredName) {
    super(module);
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
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return PsiUtil.isLanguageLevel9OrHigher(file) &&
           startElement instanceof PsiJavaModule &&
           startElement.getManager().isInProject(startElement) &&
           getLBrace((PsiJavaModule)startElement) != null;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    addRequiresInstruction((PsiJavaModule)startElement, myRequiredName);
  }

  @SuppressWarnings("UnusedReturnValue") // used in Kotlin plugin
  public static boolean addRequiresInstruction(@NotNull PsiJavaModule module, @NotNull String requiredName) {
    if (!module.isValid() || findRequiresInstruction(module, requiredName) != null) {
      return false;
    }
    PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(module.getProject()).getParserFacade();
    PsiStatement requiresStatement = parserFacade.createModuleStatementFromText(PsiKeyword.REQUIRES + ' ' + requiredName);

    PsiElement addingPlace = findAddingPlace(module);
    if (addingPlace != null) {
      addingPlace.getParent().addAfter(requiresStatement, addingPlace);
      return true;
    }
    return false;
  }

  private static PsiRequiresStatement findRequiresInstruction(@NotNull PsiJavaModule module, @NotNull String requiredName) {
    return ContainerUtil.find(module.getRequires(), statement -> requiredName.equals(statement.getModuleName()));
  }

  @Nullable
  private static PsiElement findAddingPlace(@NotNull PsiJavaModule module) {
    PsiElement addingPlace = ContainerUtil.iterateAndGetLastItem(module.getRequires());
    return addingPlace != null ? addingPlace : getLBrace(module);
  }

  @Nullable
  private static PsiElement getLBrace(@NotNull PsiJavaModule module) {
    PsiJavaModuleReferenceElement nameElement = module.getNameIdentifier();
    for (PsiElement element = nameElement.getNextSibling(); element != null; element = element.getNextSibling()) {
      if (PsiUtil.isJavaToken(element, JavaTokenType.LBRACE)) {
        return element;
      }
    }
    return null; // module-info is incomplete
  }
}
