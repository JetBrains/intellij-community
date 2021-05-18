// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.extraction;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface DebugToolWindowContentExtractor {
  ExtensionPointName<DebugToolWindowContentExtractor> EP_NAME = ExtensionPointName.create("com.intellij.debugToolWindowContentExtractor");
  Key<Boolean> ENABLE_DEBUG_TAB = Key.create("DebugToolWindowContentExtractor.EnableDebugTab");
  
  boolean isApplicable(@NotNull Content content);
  
  boolean isEnabled(@NotNull Content content);
}
