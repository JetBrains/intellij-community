/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.MemberChooserObject;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;

import javax.swing.*;

/**
* User: anna
* Date: 8/2/12
*/
public class ParameterClassMember implements ClassMember {
  public static final ParameterClassMember[] EMPTY_ARRAY = new ParameterClassMember[0];
  private final PsiParameter myParameter;

  public ParameterClassMember(PsiParameter parameter) {
    myParameter = parameter;
  }

  @Override
  public MemberChooserObject getParentNodeDelegate() {
    return new PsiMethodMember((PsiMethod)myParameter.getDeclarationScope());
  }

  @Override
  public void renderTreeNode(SimpleColoredComponent component, JTree tree) {
    SpeedSearchUtil.appendFragmentsForSpeedSearch(tree, getText(), SimpleTextAttributes.REGULAR_ATTRIBUTES, false, component);
    component.setIcon(myParameter.getIcon(0));
  }

  @Override
  public String getText() {
    return myParameter.getName();
  }

  public PsiParameter getParameter() {
    return myParameter;
  }
}
