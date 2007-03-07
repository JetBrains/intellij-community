/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 05-Jan-2007
 */
package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.ui.RefElementNode;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import org.jetbrains.annotations.Nullable;

public class OfflineRefElementNode extends RefElementNode {

  public OfflineRefElementNode(OfflineProblemDescriptor descriptor, final InspectionTool inspectionTool) {
    super(descriptor, inspectionTool);
  }

  @Nullable
  public RefElement getElement() {
    if (userObject instanceof RefElement) {
      return (RefElement)userObject;
    }
    if (userObject == null) return null;
    final RefElement refElement = ((OfflineProblemDescriptor)userObject).getRefElement(myTool.getContext().getRefManager());
    setUserObject(refElement);
    return refElement;
  }

  @Nullable
  public OfflineProblemDescriptor getDescriptor() {
    if (userObject instanceof OfflineProblemDescriptor) {
      return (OfflineProblemDescriptor)userObject;
    }
    return null;
  }
}