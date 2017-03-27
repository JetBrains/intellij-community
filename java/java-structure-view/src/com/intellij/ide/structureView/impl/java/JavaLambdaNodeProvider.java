/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.structureView.impl.java;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.util.FileStructureNodeProvider;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.util.PropertyOwner;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMember;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class JavaLambdaNodeProvider implements FileStructureNodeProvider<JavaLambdaTreeElement>, PropertyOwner {
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
    return new ActionPresentationData(getCheckBoxText(), null, AllIcons.Nodes.Function);
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
