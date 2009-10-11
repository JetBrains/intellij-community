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
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

public class LibrariesElement {
  private final Module myModule;
  private final Project myProject;

  public LibrariesElement(final Module module, final Project project) {
    myModule = module;
    myProject = project;
  }

  public Module getModule() {
    return myModule;
  }

  public Project getProject() {
    return myProject;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LibrariesElement)) return false;

    final LibrariesElement librariesElement = (LibrariesElement)o;

    if (myModule != null ? !myModule.equals(librariesElement.myModule) : librariesElement.myModule != null) return false;
    if (!myProject.equals(librariesElement.myProject)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myModule != null ? myModule.hashCode() : 0);
    result = 29 * result + myProject.hashCode();
    return result;
  }
}
