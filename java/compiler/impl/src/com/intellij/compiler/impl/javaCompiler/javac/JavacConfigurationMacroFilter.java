/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compiler.impl.javaCompiler.javac;

import com.intellij.openapi.application.PathMacroFilter;
import org.jdom.Attribute;
import org.jdom.Element;

/**
 * @author nik
 */
public class JavacConfigurationMacroFilter extends PathMacroFilter {
  @Override
  public boolean recursePathMacros(Attribute attribute) {
    if (attribute.getName().equals("value")) {
      Element parent = attribute.getParent();
      if (parent != null && "option".equals(parent.getName()) && "ADDITIONAL_OPTIONS_STRING".equals(parent.getAttributeValue("name"))) {
        Element grandParent = parent.getParentElement();
        return grandParent != null && grandParent.getName().equals("component")
               && "JavacSettings".equals(grandParent.getAttributeValue("name"));
      }
    }
    return false;
  }
}
