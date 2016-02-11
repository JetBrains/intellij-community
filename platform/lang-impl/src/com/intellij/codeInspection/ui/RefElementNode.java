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

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.ComputableIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.MutableTreeNode;

/**
 * @author max
 */
public class RefElementNode extends InspectionTreeNode {
  private boolean myHasDescriptorsUnder = false;
  private CommonProblemDescriptor mySingleDescriptor = null;
  protected final InspectionToolPresentation myToolPresentation;
  private final ComputableIcon myIcon = new ComputableIcon(new Computable<Icon>() {
    @Override
    public Icon compute() {
      final RefEntity refEntity = getElement();
      if (refEntity == null) {
        return null;
      }
      return refEntity.getIcon(false);
    }
  });

  public RefElementNode(@NotNull Object userObject, @NotNull InspectionToolPresentation presentation) {
    super(userObject);
    myToolPresentation = presentation;
  }

  public RefElementNode(@NotNull RefElement element, @NotNull InspectionToolPresentation presentation) {
    this((Object)element, presentation);
  }

  public boolean hasDescriptorsUnder() {
    return myHasDescriptorsUnder;
  }

  @Nullable
  public RefEntity getElement() {
    return (RefEntity)getUserObject();
  }

  @Override
  @Nullable
  public Icon getIcon(boolean expanded) {
    return myIcon.getIcon();
  }

  public String toString() {
    final RefEntity element = getElement();
    if (element == null || !element.isValid()) {
      return InspectionsBundle.message("inspection.reference.invalid");
    }
    return element.getRefManager().getRefinedElement(element).getQualifiedName();
  }

  @Override
  public boolean isValid() {
    final RefEntity refEntity = getElement();
    return refEntity != null && refEntity.isValid();
  }

  @Override
  public boolean isResolved() {
    return myToolPresentation.isElementIgnored(getElement());
  }


  @Override
  public void ignoreElement() {
    myToolPresentation.ignoreCurrentElement(getElement());
    super.ignoreElement();
  }

  @Override
  public void amnesty() {
    myToolPresentation.amnesty(getElement());
    super.amnesty();
  }

  @Override
  public FileStatus getNodeStatus() {
    return  myToolPresentation.getElementStatus(getElement());
  }

  @Override
  public void add(MutableTreeNode newChild) {
    super.add(newChild);
    if (newChild instanceof ProblemDescriptionNode) {
      myHasDescriptorsUnder = true;
    }
  }

  public void setProblem(@NotNull CommonProblemDescriptor descriptor) {
    mySingleDescriptor = descriptor;
  }

  public CommonProblemDescriptor getProblem() {
    return mySingleDescriptor;
  }

}
