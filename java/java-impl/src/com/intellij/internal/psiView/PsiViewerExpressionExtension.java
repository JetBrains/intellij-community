// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.psiView;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;

import javax.swing.*;

public class PsiViewerExpressionExtension extends JavaPsiViewerExtension {
  @Override
  public String getName() {
    return "Java Expression";
  }

  @Override
  public Icon getIcon() {
    return PlatformIcons.CLASS_INITIALIZER;
  }

  @Override
  public PsiElement createElement(Project project, String text) {
    return getFactory(project).createExpressionFromText(text, null);
  }
}
