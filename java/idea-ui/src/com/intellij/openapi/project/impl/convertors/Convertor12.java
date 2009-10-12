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
package com.intellij.openapi.project.impl.convertors;

import org.jdom.Element;

@SuppressWarnings({"HardCodedStringLiteral"})
public class Convertor12 {
  private static final String OLD_PROJECT_ROOT_CONTAINER_CLASS = "com.intellij.project.ProjectRootContainer";
  private static final String NEW_PROJECT_ROOT_CONTAINER_CLASS = "com.intellij.projectRoots.ProjectRootContainer";

  public static void execute(Element root) {
    Element rootContComponent = Util.findComponent(root, OLD_PROJECT_ROOT_CONTAINER_CLASS);
    if (rootContComponent != null) {
      rootContComponent.setAttribute("class", NEW_PROJECT_ROOT_CONTAINER_CLASS);
    }
  }
}