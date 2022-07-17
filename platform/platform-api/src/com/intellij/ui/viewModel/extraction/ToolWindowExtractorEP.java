// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.extraction;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;

/**
 * A simple extension point to specify non-default toolwindow extraction mode for a given toolwindow.
 * This extension point is applied equally to Code With Me guests and Remote Dev controller client.
 * If more flexibility is desired, it's recommended to use {@link ToolWindowViewModelExtractor}<br/>
 * Refer to {@link ToolWindowExtractorMode} for additional info, available modes, and their meaning
 *
 * @see ViewModelToolWindowFactory
 */
@ApiStatus.Experimental
public class ToolWindowExtractorEP {
  public static final ExtensionPointName<ToolWindowExtractorEP> EP_NAME = ExtensionPointName.create("com.intellij.toolWindowExtractorMode");

  /**
   * Toolwindow ID to set extraction mode for
   */
  @Attribute("id")
  @RequiredElement
  public String id;

  /**
   * Requested extraction mode
   */
  @Attribute("mode")
  @RequiredElement
  public ToolWindowExtractorMode mode;
}
