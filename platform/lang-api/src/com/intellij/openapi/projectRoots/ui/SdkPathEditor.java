/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.projectRoots.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SdkPathEditor extends PathEditor {
  private final String myDisplayName;
  private final OrderRootType myOrderRootType;

  public SdkPathEditor(String displayName, @NotNull OrderRootType orderRootType, FileChooserDescriptor descriptor) {
    super(descriptor);
    myDisplayName = displayName;
    myOrderRootType = orderRootType;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  @NotNull
  public OrderRootType getOrderRootType() {
    return myOrderRootType;
  }

  public void apply(SdkModificator sdkModificator) {
    sdkModificator.removeRoots(myOrderRootType);
    // add all items
    for (int i = 0; i < getRowCount(); i++) {
      sdkModificator.addRoot(getValueAt(i), myOrderRootType);
    }
    setModified(false);
  }

  public void reset(@Nullable SdkModificator modificator) {
    if (modificator != null) {
      resetPath(ContainerUtil.newArrayList(modificator.getRoots(myOrderRootType)));
    }
    else {
      setEnabled(false);
    }
  }
}
