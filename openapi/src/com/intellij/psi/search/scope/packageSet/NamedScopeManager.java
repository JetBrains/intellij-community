/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi.search.scope.packageSet;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class NamedScopeManager extends NamedScopesHolder implements ProjectComponent {
  public static NamedScopeManager getInstance(Project project) {
    return project.getComponent(NamedScopeManager.class);
  }

  @NotNull
  public String getComponentName() {
    return "NamedScopeManager";
  }

  public void initComponent() {}
  public void disposeComponent() {}
  public void projectOpened() {}
  public void projectClosed() {}

  public String getDisplayName() {
    return IdeBundle.message("local.scopes.node.text");
  }
}