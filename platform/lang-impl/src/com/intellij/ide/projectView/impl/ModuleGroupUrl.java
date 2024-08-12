// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import java.util.List;


public final class ModuleGroupUrl extends AbstractUrl {
  private static final @NonNls String ELEMENT_TYPE = "module_group";

  public ModuleGroupUrl(String url) {
    super(url, null, ELEMENT_TYPE);
  }

  @Override
  public Object[] createPath(Project project) {
    List<String> groupPath = StringUtil.split(url, ";");
    return new Object[]{new ModuleGroup(groupPath)};
  }

  @Override
  protected AbstractUrl createUrl(String moduleName, String url) {
      return new ModuleGroupUrl(url);
  }

  @Override
  public AbstractUrl createUrlByElement(Object element) {
    if (element instanceof ModuleGroup group) {
      final String[] groupPath = group.getGroupPath();
      return new ModuleGroupUrl(StringUtil.join(groupPath, ";"));
    }
    return null;
  }
}
