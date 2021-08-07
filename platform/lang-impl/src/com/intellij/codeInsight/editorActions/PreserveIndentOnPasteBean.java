// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;


public class PreserveIndentOnPasteBean {
  public static final ExtensionPointName<PreserveIndentOnPasteBean> EP_NAME = ExtensionPointName.create("com.intellij.preserveIndentOnPaste");

  @Attribute("fileType")
  public String fileType;
}
