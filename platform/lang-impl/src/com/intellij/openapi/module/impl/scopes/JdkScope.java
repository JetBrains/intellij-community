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

package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * @author max
 */
public class JdkScope extends GlobalSearchScope {
  private final LinkedHashSet<VirtualFile> myEntries = new LinkedHashSet<VirtualFile>();
  private final String myJdkName;
  private final ProjectFileIndex myIndex;

  public JdkScope(Project project, JdkOrderEntry jdk) {
    super(project);
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myJdkName = jdk.getJdkName();
    ContainerUtil.addAll(myEntries, jdk.getRootFiles(OrderRootType.CLASSES));
  }

  public int hashCode() {
    return myJdkName.hashCode();
  }

  public boolean equals(Object object) {
    if (object == this) return true;
    if (object.getClass() != JdkScope.class) return false;

    final JdkScope that = ((JdkScope)object);
    return that.myJdkName.equals(myJdkName);
  }

  public boolean contains(VirtualFile file) {
    return myEntries.contains(getFileRoot(file));
  }

  private VirtualFile getFileRoot(VirtualFile file) {
    if (myIndex.isInLibraryClasses(file)) {
      return myIndex.getClassRootForFile(file);
    }
    if (myIndex.isInContent(file)) {
      return myIndex.getSourceRootForFile(file);
    }
    return null;
  }

  public int compare(VirtualFile file1, VirtualFile file2) {
    final VirtualFile r1 = getFileRoot(file1);
    final VirtualFile r2 = getFileRoot(file2);
    for (VirtualFile root : myEntries) {
      if (r1 == root) return 1;
      if (r2 == root) return -1;
    }
    return 0;
  }

  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return false;
  }

  public boolean isSearchInLibraries() {
    return true;
  }
}
