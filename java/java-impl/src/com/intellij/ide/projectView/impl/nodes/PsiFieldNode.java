/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;

import java.util.Collection;
import java.util.Objects;

public class PsiFieldNode extends BasePsiMemberNode<PsiField>{
  public PsiFieldNode(Project project, PsiField value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  public Collection<AbstractTreeNode> getChildrenImpl() {
    return null;
  }

  @Override
  public void updateImpl(PresentationData data) {
    PsiField field = Objects.requireNonNull(getValue());
    String name;
    try {
      name = PsiFormatUtil.formatVariable(field,
                                          PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER,
                                          PsiSubstitutor.EMPTY);
    }
    catch (IndexNotReadyException e) {
      name = StringUtil.notNullize(field.getName());
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
        return field.toString();
      }
    }
    return super.getTitle();
  }
}
