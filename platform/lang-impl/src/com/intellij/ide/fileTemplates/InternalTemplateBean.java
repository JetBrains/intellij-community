// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.fileTemplates;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public final class InternalTemplateBean {
  public static final ExtensionPointName<InternalTemplateBean> EP_NAME = new ExtensionPointName<>("com.intellij.internalFileTemplate");

  @Attribute("name")
  public String name;

  @Attribute("subject")
  public String subject;
}
