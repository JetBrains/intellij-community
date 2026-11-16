// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.targets.AliasingPsiTarget;
import com.intellij.psi.targets.AliasingPsiTargetMapper;
import com.intellij.refactoring.modplan.rename.ModPlanBridgeRenameProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class RenameAliasingPomTargetProcessor extends RenamePsiElementProcessor {
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

  /**
   * Renames an element of this processor on the ModPlan engine, see {@link ModPlanBridgeRenameProvider}.
   * <p>
   * An aliasing POM target is a name that a framework derives from the name of another element. {@link AliasingPsiTarget} converts the
   * name in both directions. The rename of the target renames that element, and the rename of an element renames the targets that an
   * {@link AliasingPsiTargetMapper} gives for it.
   * <pre>{@code
   * class UserService {}   // a Spring service: the bean name "userService" is the aliasing target of the class
   * // rename the bean name to "accountService"
   * class AccountService {}
   * }</pre>
   */
  @ApiStatus.Internal
  public static final class ModPlanRenameAliasingPomTargetProvider extends ModPlanBridgeRenameProvider {
    public ModPlanRenameAliasingPomTargetProvider() {
      super(RenameAliasingPomTargetProcessor.class);
    }
  }
}
