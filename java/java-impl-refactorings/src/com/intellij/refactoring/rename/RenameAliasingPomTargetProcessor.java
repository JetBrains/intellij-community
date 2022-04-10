// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.targets.AliasingPsiTarget;
import com.intellij.psi.targets.AliasingPsiTargetMapper;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class RenameAliasingPomTargetProcessor extends RenamePsiElementProcessor {
  private static final Logger LOG = Logger.getInstance(RenameAliasingPomTargetProcessor.class);

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof PomTarget || element instanceof PomTargetPsiElement;
  }

  @Override
  public void prepareRenaming(@NotNull PsiElement element, @NotNull String newName, @NotNull Map<PsiElement, String> allRenames) {
    PomTarget target = null;
    if (element instanceof PomTargetPsiElement) {
      target = ((PomTargetPsiElement)element).getTarget();
    }
    else if (element instanceof PomTarget) {
      target = (PomTarget)element;
    }

    if (target != null) {
      if (target instanceof AliasingPsiTarget) {
        prepareAliasingPsiTargetRenaming((AliasingPsiTarget)target, newName, allRenames);
      }
      for (AliasingPsiTargetMapper mapper : AliasingPsiTargetMapper.EP_NAME.getExtensionList()) {
        for (AliasingPsiTarget psiTarget : mapper.getTargets(target)) {
          PsiElement psiElement = PomService.convertToPsi(psiTarget);
          String name = psiTarget.getNameAlias(newName);

          String definedName = allRenames.put(psiElement, name);
          if (definedName != null) {
            LOG.assertTrue(definedName.equals(name), "target: " + psiTarget + "; " +
                                                     "defined name: " + definedName + "; " +
                                                     "name: " + name + "; " +
                                                     "mapper: " + mapper);
          }
          else {
            if (name != null) prepareRenaming(psiElement, name, allRenames);
          }
        }
      }
    }
  }


  public static void prepareAliasingPsiTargetRenaming(@NotNull AliasingPsiTarget aliasingPsiTarget,
                                                      @NotNull String newName,
                                                      @NotNull Map<PsiElement, String> allRenames) {
    PsiElement psiElement = aliasingPsiTarget.getNavigationElement();
    if (psiElement instanceof PsiNamedElement) {
      String newTargetName = aliasingPsiTarget.getTargetName(newName);
      if (!newTargetName.equals(((PsiNamedElement)psiElement).getName())) {
        allRenames.put(psiElement, newTargetName);
      }
    }
  }
}
