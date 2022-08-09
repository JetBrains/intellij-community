// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.settings;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;

public class CodeVisionSettingsPreviewLanguage {

  public static final ExtensionPointName<CodeVisionSettingsPreviewLanguage> EP_NAME =
    ExtensionPointName.create("com.intellij.codeInsight.codeVisionSettingsPreviewLanguage");

  @Attribute("modelId")
  @RequiredElement
  public String modelId;

  @Attribute("language")
  @RequiredElement
  public String language;

}
