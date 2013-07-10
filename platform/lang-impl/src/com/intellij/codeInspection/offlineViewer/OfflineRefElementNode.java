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

/*
 * User: anna
 * Date: 05-Jan-2007
 */
package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.codeInspection.ui.RefElementNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OfflineRefElementNode extends RefElementNode {
  public OfflineRefElementNode(@NotNull OfflineProblemDescriptor descriptor, @NotNull InspectionToolPresentation presentation) {
    super(descriptor, presentation);
  }

  @Override
  @Nullable
  public RefEntity getElement() {
    if (userObject instanceof RefEntity) {
      return (RefEntity)userObject;
    }
    if (userObject == null) return null;
    final RefEntity refElement = ((OfflineProblemDescriptor)userObject).getRefElement(myToolPresentation.getContext().getRefManager());
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