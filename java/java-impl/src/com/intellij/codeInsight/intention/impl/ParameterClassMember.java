// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.MemberChooserObject;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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
  public @NotNull String getText() {
    return myParameter.getName();
  }

  @Override
  public @Nullable Icon getIcon() {
    return myParameter.getIcon(0);
  }

  public PsiParameter getParameter() {
    return myParameter;
  }
}
