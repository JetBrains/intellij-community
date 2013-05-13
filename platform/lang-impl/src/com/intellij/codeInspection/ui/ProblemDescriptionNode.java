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

package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author max
 */
public class ProblemDescriptionNode extends InspectionTreeNode {
  protected RefEntity myElement;
  private final CommonProblemDescriptor myDescriptor;
  protected final DescriptorProviderInspection myTool;

  public ProblemDescriptionNode(final Object userObject, final DescriptorProviderInspection tool) {
    super(userObject);
    myTool = tool;
    myDescriptor = null;
  }

  public ProblemDescriptionNode(RefEntity element,
                                CommonProblemDescriptor descriptor,
                                DescriptorProviderInspection descriptorProviderInspection) {
    super(descriptor);
    myElement = element;
    myDescriptor = descriptor;
    myTool = descriptorProviderInspection;
  }

  @Nullable
  public RefEntity getElement() {
    return myElement;
  }

  @Nullable
  public CommonProblemDescriptor getDescriptor() {
    return myDescriptor;
  }

  @Override
  public Icon getIcon(boolean expanded) {
    if (myDescriptor instanceof ProblemDescriptorBase) {
      ProblemHighlightType problemHighlightType = ((ProblemDescriptorBase)myDescriptor).getHighlightType();
      if (problemHighlightType == ProblemHighlightType.ERROR) return AllIcons.General.Error;
      if (problemHighlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING) return AllIcons.General.Warning;
    }
    return AllIcons.General.Information;
  }

  @Override
  public int getProblemCount() {
    return 1;
  }

  @Override
  public boolean isValid() {
    if (myElement instanceof RefElement && !myElement.isValid()) return false;
    final CommonProblemDescriptor descriptor = getDescriptor();
    if (descriptor instanceof ProblemDescriptor) {
      final PsiElement psiElement = ((ProblemDescriptor)descriptor).getPsiElement();
      return psiElement != null && psiElement.isValid();
    }
    return true;
  }


  @Override
  public boolean isResolved() {
    return myElement instanceof RefElement && myTool.isProblemResolved(myElement, getDescriptor());
  }

  @Override
  public void ignoreElement() {
    myTool.ignoreCurrentElementProblem(getElement(), getDescriptor());
  }

  @Override
  public void amnesty() {
    myTool.amnesty(getElement());
  }

  @Override
  public FileStatus getNodeStatus() {
    if (myElement instanceof RefElement){
      return myTool.getProblemStatus(myDescriptor);
    }
    return FileStatus.NOT_CHANGED;
  }

  public String toString() {
    CommonProblemDescriptor descriptor = getDescriptor();
    if (descriptor == null) return "";
    PsiElement element = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null;

    return ProblemDescriptorUtil.renderDescriptionMessage(descriptor, element, true)/*.replaceAll("<[^>]*>", "")*/;
  }
}
