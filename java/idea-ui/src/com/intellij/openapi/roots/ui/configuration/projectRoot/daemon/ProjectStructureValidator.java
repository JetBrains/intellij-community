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
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * User: ksafonov
 */
public abstract class ProjectStructureValidator {

  private static final ExtensionPointName<ProjectStructureValidator> EP_NAME =
    ExtensionPointName.create("com.intellij.projectStructureValidator");

  public static void check(ProjectStructureElement element, ProjectStructureProblemsHolder problemsHolder) {
    for (ProjectStructureValidator validator : EP_NAME.getExtensions()) {
      if (validator.checkElement(element, problemsHolder)) {
        return;
      }
    }
    element.check(problemsHolder);
  }
  
  protected boolean checkElement(ProjectStructureElement element, ProjectStructureProblemsHolder problemsHolder) {
    return false;
  }
  
}
