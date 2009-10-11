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
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.vcs.FileStatus;
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
  protected InspectionTool myTool;

  public RefElementNode(final Object userObject, final InspectionTool tool) {
    super(userObject);
    myTool = tool;
  }

  public RefElementNode(@NotNull RefElement element, final InspectionTool inspectionTool) {
    super(element);
    myTool = inspectionTool;
  }

  public boolean hasDescriptorsUnder() { return myHasDescriptorsUnder; }

  @Nullable
  public RefEntity getElement() {
    return (RefEntity)getUserObject();
  }

  @Nullable
  public Icon getIcon(boolean expanded) {
    final RefEntity refEntity = getElement();
    if (refEntity == null) {
      return null;
    }
    return refEntity.getIcon(expanded);
  }

  public int getProblemCount() {
    return Math.max(1, super.getProblemCount());
  }

  public String toString() {
    final RefEntity element = getElement();
    if (element == null || !element.isValid()) {
      return InspectionsBundle.message("inspection.reference.invalid");
    }
    return element.getRefManager().getRefinedElement(element).getQualifiedName();
  }

  public boolean isValid() {
    final RefEntity refEntity = getElement();
    return refEntity != null && refEntity.isValid();
  }

  public boolean isResolved() {
    return myTool.isElementIgnored(getElement());
  }


  public void ignoreElement() {
    myTool.ignoreCurrentElement(getElement());
    super.ignoreElement();
  }

  public void amnesty() {
    myTool.amnesty(getElement());
    super.amnesty();
  }

  public FileStatus getNodeStatus() {
    return  myTool.getElementStatus(getElement());    
  }

  public void add(MutableTreeNode newChild) {
    super.add(newChild);
    if (newChild instanceof ProblemDescriptionNode) {
      myHasDescriptorsUnder = true;
    }
  }

  public void setProblem(CommonProblemDescriptor descriptor) {
    mySingleDescriptor = descriptor;
  }

  public CommonProblemDescriptor getProblem() {
    return mySingleDescriptor;
  }

}
