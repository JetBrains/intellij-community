// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView.impl.java;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.util.FileStructureNodeProvider;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.PropertyOwner;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMember;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class JavaLambdaNodeProvider
  implements FileStructureNodeProvider<JavaLambdaTreeElement>, PropertyOwner, DumbAware {
  public static final String ID = "SHOW_LAMBDA";
  public static final String JAVA_LAMBDA_PROPERTY_NAME = "java.lambda.provider";

  @NotNull
  @Override
  public List<JavaLambdaTreeElement> provideNodes(@NotNull TreeElement node) {
    if (!(node instanceof PsiTreeElementBase)) {
      return Collections.emptyList();
    }
    PsiElement element = ((PsiTreeElementBase)node).getElement();
    return SyntaxTraverser.psiTraverser(element)
      .expand(o -> o == element || !(o instanceof PsiMember || o instanceof PsiLambdaExpression))
      .filter(PsiLambdaExpression.class)
      .filter(o -> o != element)
      .map(o -> new JavaLambdaTreeElement(o))
      .toList();
  }

  @NotNull
  @Override
  public String getCheckBoxText() {
    return "Show Lambdas";
  }

  @NotNull
  @Override
  public Shortcut[] getShortcut() {
    return new Shortcut[]{KeyboardShortcut.fromString(SystemInfo.isMac ? "meta L" : "control L")};
  }

  @NotNull
  @Override
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(getCheckBoxText(), null, AllIcons.Nodes.Lambda);
  }

  @NotNull
  @Override
  public String getName() {
    return ID;
  }

  @NotNull
  @Override
  public String getPropertyName() {
    return JAVA_LAMBDA_PROPERTY_NAME;
  }
}
