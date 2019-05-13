// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class ApplicationRunLineMarkerProvider extends RunLineMarkerContributor {
  @Nullable
  @Override
  public Info getInfo(@NotNull final PsiElement e) {
    if (Registry.is("ide.jvm.run.marker")) return null;
    if (isIdentifier(e)) {
      PsiElement element = e.getParent();
      if (element instanceof PsiClass && PsiMethodUtil.findMainInClass((PsiClass)element) != null ||
          element instanceof PsiMethod && "main".equals(((PsiMethod)element).getName()) && PsiMethodUtil.isMainMethod((PsiMethod)element)) {
        final AnAction[] actions = ExecutorAction.getActions();
        return new Info(AllIcons.RunConfigurations.TestState.Run, actions, element1 -> StringUtil.join(ContainerUtil.mapNotNull(actions, action -> getText(action, element1)), "\n"));
      }
    }
    return null;
  }

  protected boolean isIdentifier(PsiElement e) {
    return e instanceof PsiIdentifier;
  }
}
