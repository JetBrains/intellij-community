// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.extraction;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;

public class ToolWindowExtractorEP {
  public static final ExtensionPointName<ToolWindowExtractorEP> EP_NAME = ExtensionPointName.create("com.intellij.toolWindowExtractorMode");

  @Attribute("id")
  @RequiredElement
  public String id;

  @Attribute("mode")
  @RequiredElement
  public ToolWindowExtractorMode mode;
}
