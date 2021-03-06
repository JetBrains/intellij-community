// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class PsiMethodNode extends BasePsiMemberNode<PsiMethod>{
  public PsiMethodNode(Project project, @NotNull PsiMethod value, @NotNull ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  public Collection<AbstractTreeNode<?>> getChildrenImpl() {
    return null;
  }

  @Override
  public void updateImpl(@NotNull PresentationData data) {
    PsiMethod method = getValue();
    assert method != null;
    String name;
    try {
      name = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME |
                                                                      PsiFormatUtilBase.SHOW_TYPE |
                                                                      PsiFormatUtilBase.TYPE_AFTER |
                                                                      PsiFormatUtilBase.SHOW_PARAMETERS,
                                        PsiFormatUtilBase.SHOW_TYPE);
    }
    catch (IndexNotReadyException e) {
      name = method.getName();
    }
    int c = name.indexOf('\n');
    if (c > -1) {
      name = name.substring(0, c - 1);
    }
    data.setPresentableText(name);
  }

  @Override
  public boolean isAlwaysLeaf() {
    return true;
  }

  public boolean isConstructor() {
    final PsiMethod psiMethod = getValue();
    return psiMethod != null && psiMethod.isConstructor();
  }

  @Override
  public int getWeight() {
    return isConstructor() ? 40 : 50;
  }

  @Override
  public String getTitle() {
    final PsiMethod method = getValue();
    if (method != null) {
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        return aClass.getQualifiedName();
      }
      else {
        return method.getName();
      }
    }
    return super.getTitle();
  }
}
