/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInspection.ProblemDescriptorUtil;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.codeInspection.ui.ProblemDescriptionNode;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class OfflineProblemDescriptorNode extends ProblemDescriptionNode {
  private final OfflineDescriptorResolveResult myDescriptorResolveResult;

  private OfflineProblemDescriptorNode(OfflineDescriptorResolveResult descriptorResolveResult,
                                       @NotNull InspectionToolPresentation presentation,
                                       @NotNull OfflineProblemDescriptor offlineDescriptor) {
    super(descriptorResolveResult.getResolvedEntity(), descriptorResolveResult.getResolvedDescriptor(), presentation, false, offlineDescriptor::getLine);
    myDescriptorResolveResult = descriptorResolveResult;
    if (descriptorResolveResult.getResolvedDescriptor() == null) {
      setUserObject(offlineDescriptor);
    }
    init(presentation.getContext().getProject());
  }

  static OfflineProblemDescriptorNode create(@NotNull OfflineProblemDescriptor offlineDescriptor,
                                             @NotNull OfflineDescriptorResolveResult resolveResult,
                                             @NotNull InspectionToolPresentation presentation) {
    return new OfflineProblemDescriptorNode(resolveResult,
                                            presentation,
                                            offlineDescriptor);
  }

  @NotNull
  @Override
  protected String calculatePresentableName() {
    String presentableName = super.calculatePresentableName();
    return presentableName.isEmpty() && getUserObject() instanceof OfflineProblemDescriptor
           ? ProblemDescriptorUtil.unescapeTags(StringUtil.notNullize(((OfflineProblemDescriptor)getUserObject()).getDescription())).trim()
           : presentableName;
  }

  @Override
  protected boolean calculateIsValid() {
    boolean isValid = super.calculateIsValid();
    if (!isValid) {
      if (getDescriptor() == null && !(getToolWrapper() instanceof LocalInspectionToolWrapper)) {
        isValid = myElement != null && myElement.isValid();
      }
    }
    return isValid;
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
