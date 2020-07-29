// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class GlobalInspectionContextUtil {
  public static RefElement retrieveRefElement(@NotNull PsiElement element, @NotNull GlobalInspectionContext globalContext) {
    PsiFile elementFile = element.getContainingFile();
    RefElement refElement = globalContext.getRefManager().getReference(elementFile);
    if (refElement == null) {
      PsiElement context = InjectedLanguageManager.getInstance(elementFile.getProject()).getInjectionHost(elementFile);
      if (context != null) refElement = globalContext.getRefManager().getReference(context.getContainingFile());
    }
    return refElement;
  }

  /**
   * @deprecated use {@link #canRunInspections(Project, boolean, Runnable)}
   */
  @Deprecated
  public static boolean canRunInspections(@NotNull Project project, final boolean online) {
    return canRunInspections(project, online, () -> { });
  }

  public static boolean canRunInspections(@NotNull Project project,
                                          final boolean online,
                                          @NotNull Runnable rerunAction) {
    if( InspectionExtensionsFactory.EP_NAME.getExtensionList().size() == 0){
      return true;
    }
    for (InspectionExtensionsFactory factory : InspectionExtensionsFactory.EP_NAME.getExtensionList()) {
      if (factory.isProjectConfiguredToRunInspections(project, online, rerunAction)) {
        return true;
      }
    }
    return false;
  }
}
