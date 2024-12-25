// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.util.FileStructureNodeProvider;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.client.ClientSystemInfo;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.PropertyOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMember;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

public class JavaLambdaNodeProvider implements FileStructureNodeProvider<JavaLambdaTreeElement>, PropertyOwner, DumbAware {
  public static final @NonNls String ID = "SHOW_LAMBDA";
  public static final @NonNls String JAVA_LAMBDA_PROPERTY_NAME = "java.lambda.provider";

  @Override
  public @NotNull @Unmodifiable List<JavaLambdaTreeElement> provideNodes(@NotNull TreeElement node) {
    if (!(node instanceof PsiTreeElementBase)) {
      return Collections.emptyList();
    }
    PsiElement element = ((PsiTreeElementBase<?>)node).getElement();
    return SyntaxTraverser.psiTraverser(element)
      .expand(o -> o == element || !(o instanceof PsiMember || o instanceof PsiLambdaExpression))
      .filter(PsiLambdaExpression.class)
      .filter(o -> o != element)
      .map(o -> new JavaLambdaTreeElement(o))
      .toList();
  }

  @Override
  public @NotNull String getCheckBoxText() {
    return JavaStructureViewBundle.message("file.structure.toggle.show.collapse.show.lambdas");
  }

  @Override
  public Shortcut @NotNull [] getShortcut() {
    return new Shortcut[]{KeyboardShortcut.fromString(ClientSystemInfo.isMac() ? "meta L" : "control L")};
  }

  @Override
  public @NotNull ActionPresentation getPresentation() {
    return new ActionPresentationData(getCheckBoxText(), null, AllIcons.Nodes.Lambda);
  }

  @Override
  public @NotNull String getName() {
    return ID;
  }

  @Override
  public @NotNull String getPropertyName() {
    return JAVA_LAMBDA_PROPERTY_NAME;
  }
}
