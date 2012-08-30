/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ide.projectView.impl;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;


public class ModuleGroupUrl extends AbstractUrl {
  @NonNls private static final String ELEMENT_TYPE = "module_group";

  public ModuleGroupUrl(String url) {
    super(url, null, ELEMENT_TYPE);
  }

  @Override
  public Object[] createPath(Project project) {
    final String[] groupPath = url.split(";");
    return new Object[]{new ModuleGroup(groupPath)};
  }

  @Override
  protected AbstractUrl createUrl(String moduleName, String url) {
      return new ModuleGroupUrl(url);
  }

  @Override
  public AbstractUrl createUrlByElement(Object element) {
    if (element instanceof ModuleGroup) {
      ModuleGroup group = (ModuleGroup)element;
      final String[] groupPath = group.getGroupPath();
      StringBuffer sb = new StringBuffer();
      for (int i = 0; i < groupPath.length; i++) {
        String s = groupPath[i];
        sb.append(s);
        sb.append(";");
      }
      return new ModuleGroupUrl(sb.toString());
    }
    return null;
  }
}
