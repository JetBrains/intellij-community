// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

import java.util.Objects;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o instanceof LibrariesElement librariesElement &&
           Objects.equals(myModule, librariesElement.myModule) &&
           myProject.equals(librariesElement.myProject);
  }

  @Override
  public int hashCode() {
    int result;
    result = (myModule != null ? myModule.hashCode() : 0);
    result = 29 * result + myProject.hashCode();
    return result;
  }
}
