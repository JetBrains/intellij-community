// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.favoritesTreeView.smartPointerPsiNodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class FieldSmartPointerNode extends BaseSmartPointerPsiNode<SmartPsiElementPointer>{
  public FieldSmartPointerNode(@NotNull Project project, @NotNull PsiField value, @NotNull ViewSettings viewSettings) {
    super(project, SmartPointerManager.getInstance(project).createSmartPsiElementPointer(value), viewSettings);
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildrenImpl() {
    return Collections.emptyList();
  }

  @Override
  public void updateImpl(@NotNull PresentationData data) {
    String name = PsiFormatUtil.formatVariable(
      (PsiField)getPsiElement(),
      PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER | PsiFormatUtilBase.SHOW_INITIALIZER,
        PsiSubstitutor.EMPTY);
    int c = name.indexOf('\n');
    if (c > -1) {
      name = name.substring(0, c - 1);
    }
    data.setPresentableText(name);
  }
}
