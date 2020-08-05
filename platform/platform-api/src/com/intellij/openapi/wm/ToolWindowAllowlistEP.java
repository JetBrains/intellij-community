// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.wm;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * Extension point to register a toolwindow ID which is created dynamically but still should be recorder in feature usage statistics.
 * If a toolwindow is registered in xml, there's no need to use this extension point, it will be added to the whitelist automatically.
 *
 * This extension point doesn't create a toolwindow but it only adds an ID to the toolwindow whitelist.
 */
public class ToolWindowAllowlistEP extends AbstractExtensionPointBean {
  public static final ExtensionPointName<ToolWindowAllowlistEP> EP_NAME = ExtensionPointName.create("com.intellij.toolWindowAllowlist");

  @Attribute("id")
  public String id;
}
