// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInspection.ProblemDescriptorUtil;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.ui.ProblemDescriptionNode;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public final class OfflineProblemDescriptorNode extends ProblemDescriptionNode {
  private final OfflineDescriptorResolveResult myDescriptorResolveResult;
  private final OfflineProblemDescriptor myOfflineDescriptor;

  public OfflineProblemDescriptorNode(OfflineDescriptorResolveResult descriptorResolveResult,
                                      @NotNull InspectionToolPresentation presentation,
                                      @NotNull OfflineProblemDescriptor offlineDescriptor,
                                      @NotNull InspectionTreeNode parent) {
    super(descriptorResolveResult.getResolvedEntity(), descriptorResolveResult.getResolvedDescriptor(), presentation, offlineDescriptor::getLine, parent);
    myDescriptorResolveResult = descriptorResolveResult;
    myOfflineDescriptor = offlineDescriptor;
  }

  @Override
  protected @NotNull String calculatePresentableName() {
    String presentableName = super.calculatePresentableName();
    return presentableName.isEmpty() && getDescriptor() == null
           ? ProblemDescriptorUtil.unescapeTags(StringUtil.notNullize(myOfflineDescriptor.getDescription())).trim()
           : presentableName;
  }

  @Override
  protected boolean calculateIsValid() {
    return false;
  }

  @Override
  public void excludeElement() {
    myDescriptorResolveResult.setExcluded(true);
  }

  @Override
  public void amnestyElement() {
    myDescriptorResolveResult.setExcluded(false);
  }

  @Override
  public boolean isExcluded() {
    return myDescriptorResolveResult.isExcluded();
  }
}
