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
 * @author max
 */
package com.intellij.psi.search;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;

public class ProjectScope {
  private static final Key<GlobalSearchScope> ALL_SCOPE_KEY = new Key<GlobalSearchScope>("ALL_SCOPE_KEY");
  private static final Key<GlobalSearchScope> PROJECT_SCOPE_KEY = new Key<GlobalSearchScope>("PROJECT_SCOPE_KEY");
  private static final Key<GlobalSearchScope> LIBRARIES_SCOPE_KEY = new Key<GlobalSearchScope>("LIBRARIES_SCOPE_KEY");

  private ProjectScope() {
  }

  public static GlobalSearchScope getAllScope(final Project project) {
    GlobalSearchScope cached = project.getUserData(ALL_SCOPE_KEY);
    return cached != null ? cached : ((UserDataHolderEx)project).putUserDataIfAbsent(ALL_SCOPE_KEY, ProjectScopeBuilder.getInstance(project).buildAllScope());
  }

  public static GlobalSearchScope getProjectScope(final Project project) {
    GlobalSearchScope cached = project.getUserData(PROJECT_SCOPE_KEY);
    return cached != null ? cached : ((UserDataHolderEx)project).putUserDataIfAbsent(PROJECT_SCOPE_KEY, ProjectScopeBuilder.getInstance(project).buildProjectScope());
  }

  public static GlobalSearchScope getLibrariesScope(Project project) {
    GlobalSearchScope cached = project.getUserData(LIBRARIES_SCOPE_KEY);
    return cached != null ? cached : ((UserDataHolderEx)project).putUserDataIfAbsent(LIBRARIES_SCOPE_KEY, ProjectScopeBuilder.getInstance(project).buildLibrariesScope());
  }
}
