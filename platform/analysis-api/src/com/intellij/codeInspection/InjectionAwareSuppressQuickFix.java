package com.intellij.codeInspection;

import com.intellij.psi.PsiElement;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

/**
 * This kind of suppression fix allows to clients to specify whether the fix should
 * be invoked on injected elements or on elements of host files.
 * <p/>
 * By default suppression fixes on injected elements are able to make suppression inside injection only.
 * Whereas implementation of this interface will be provided for suppressing inside injection and in injection host. 
 * See {@link InspectionProfileEntry#getBatchSuppressActions(PsiElement)} for details.
 */
public interface InjectionAwareSuppressQuickFix extends SuppressQuickFix {
  @NotNull
  ThreeState isShouldBeAppliedToInjectionHost();

  void setShouldBeAppliedToInjectionHost(@NotNull ThreeState shouldBeAppliedToInjectionHost);
}
