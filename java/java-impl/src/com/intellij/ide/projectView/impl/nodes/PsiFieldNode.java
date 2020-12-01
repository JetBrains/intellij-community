// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;

public class PsiFieldNode extends BasePsiMemberNode<PsiField>{
  public PsiFieldNode(Project project, @NotNull PsiField value, @NotNull ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  public Collection<AbstractTreeNode<?>> getChildrenImpl() {
    return null;
  }

  @Override
  public void updateImpl(@NotNull PresentationData data) {
    PsiField field = Objects.requireNonNull(getValue());
    String name;
    try {
      name = PsiFormatUtil.formatVariable(field,
                                          PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER,
                                          PsiSubstitutor.EMPTY);
    }
    catch (IndexNotReadyException e) {
      name = field.getName();
    }
    int c = name.indexOf('\n');
    if (c > -1) {
      name = name.substring(0, c - 1);
    }
    data.setPresentableText(name);
  }

  @Override
  public int getWeight() {
    return 70;
  }

  @Override
  public boolean isAlwaysLeaf() {
    return true;
  }

  @Override
  public String getTitle() {
    final PsiField field = getValue();
    if (field != null) {
      PsiClass aClass = field.getContainingClass();
      if (aClass != null) {
        return aClass.getQualifiedName();
      }
      else {
        @NlsSafe String fieldText = field.toString();
        return fieldText;
      }
    }
    return super.getTitle();
  }
}
