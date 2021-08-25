// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

public interface AdditionalDataConfigurable extends UnnamedConfigurable {
  void setSdk(Sdk sdk);

  /**
   *  In case of non-null value the component returned by {@link #createComponent()} will be added as a tab to myTabbedPane in SdkEditor
   */
  @Nullable
  default @NlsContexts.TabTitle String getTabName() {
    return null;
  }
}
