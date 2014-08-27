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

/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class HierarchyScopeDescriptorProvider implements ScopeDescriptorProvider {
  @NotNull
  public ScopeDescriptor[] getScopeDescriptors(final Project project) {
    if (Comparing.strEqual(ToolWindowManager.getInstance(project).getActiveToolWindowId(), ToolWindowId.TODO_VIEW)) {
      return EMPTY;
    }
    return new ScopeDescriptor[]{new ClassHierarchyScopeDescriptor(project)};
  }
}