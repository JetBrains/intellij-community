/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.execution.configuration;

import com.intellij.openapi.application.PathMacroFilter;
import org.jdom.Attribute;
import org.jdom.Element;

/**
 * @author yole
 */
public class RunConfigurationPathMacroFilter extends PathMacroFilter {
  @Override
  public boolean skipPathMacros(Attribute attribute) {
    final Element parent = attribute.getParent();
    final String attrName = attribute.getName();
    String tagName = parent.getName();
    if (tagName.equals(EnvironmentVariablesComponent.ENV) &&
        (attrName.equals(EnvironmentVariablesComponent.NAME) || attrName.equals(EnvironmentVariablesComponent.VALUE))) {
      return true;
    }

    if (tagName.equals("configuration") && attrName.equals("name")) {
      return true;
    }

    return false;
  }

  @Override
  public boolean recursePathMacros(Attribute attribute) {
    final Element parent = attribute.getParent();
    if (parent != null && "option".equals(parent.getName())) {
      final Element grandParent = parent.getParentElement();
      return grandParent != null && "configuration".equals(grandParent.getName());
    }
    return false;
  }
}
