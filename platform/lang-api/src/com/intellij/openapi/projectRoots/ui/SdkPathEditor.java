// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

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

  public @NotNull OrderRootType getOrderRootType() {
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
      resetPath(Arrays.asList(modificator.getRoots(myOrderRootType)));
    }
    else {
      setEnabled(false);
    }
  }
}
