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
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.PsiLambdaNameHelper;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

public class JavaLambdaTreeElement extends JavaClassTreeElementBase<PsiLambdaExpression> {
  public static final JavaLambdaTreeElement[] EMPTY_ARRAY = {};

  private String myName;
  private String myFunctionalName;

  public JavaLambdaTreeElement(PsiLambdaExpression lambdaExpression) {
    super(false,lambdaExpression);
  }

  @Override
  public boolean isPublic() {
    return false;
  }

  @Override
  public String getPresentableText() {
    if (myName != null) return myName;
    final PsiLambdaExpression element = getElement();

    if (element != null) {
      myName = PsiLambdaNameHelper.getVMName(element);
      return myName;
    }
    return "Lambda";
  }


  @Override
  public boolean isSearchInLocationString() {
    return true;
  }

  @Override
  public String getLocationString() {
    if (myFunctionalName == null) {
      PsiLambdaExpression lambdaExpression = getElement();
      if (lambdaExpression != null && !DumbService.isDumb(lambdaExpression.getProject())) {
        final PsiType interfaceType = lambdaExpression.getFunctionalInterfaceType();
        if (interfaceType != null) {
          myFunctionalName = interfaceType.getPresentableText();
        }
      }
    }
    return myFunctionalName;
  }

  @Override
  public String toString() {
    return super.toString() + (myFunctionalName == null ? "" : " (" + getLocationString() + ")");
  }

  @NotNull
  @Override
  public Collection<StructureViewTreeElement> getChildrenBase() {
    return Collections.emptyList();
  }

  @Override
  public Icon getIcon(boolean open) {
    return AllIcons.Nodes.Lambda;
  }
}
