// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * Adds association of internal live template context to regular context. Can be used for merging or renaming of live template contexts.
 */
public class LiveTemplateInternalContextBean {
  static final ExtensionPointName<LiveTemplateInternalContextBean> EP_NAME = new ExtensionPointName<>("com.intellij.liveTemplateInternalContext");

  @Attribute("internalContextId")
  @RequiredElement
  public String internalContextId;


  @Attribute("contextId")
  @RequiredElement
  public String contextId;
}
