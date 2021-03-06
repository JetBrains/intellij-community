// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SdkPathEditor extends PathEditor {
  private final @NlsContexts.TabTitle String myDisplayName;
  private final OrderRootType myOrderRootType;

  public SdkPathEditor(@NlsContexts.TabTitle String displayName, @NotNull OrderRootType orderRootType, FileChooserDescriptor descriptor) {
    super(descriptor);
    myDisplayName = displayName;
    myOrderRootType = orderRootType;
  }

  public @NlsContexts.TabTitle String getDisplayName() {
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
