// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public final class PreserveIndentOnPasteBean {
  public static final ExtensionPointName<PreserveIndentOnPasteBean> EP_NAME = ExtensionPointName.create("com.intellij.preserveIndentOnPaste");

  @Attribute("fileType")
  public String fileType;
}
