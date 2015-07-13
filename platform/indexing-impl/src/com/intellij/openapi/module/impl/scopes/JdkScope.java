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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Arrays;

/**
 * @author max
 */
public class JdkScope extends LibraryScopeBase {
  private final VirtualFile[] myClasses;
  private final VirtualFile[] mySources;
  private final String myJdkName;

  public JdkScope(Project project, JdkOrderEntry entry) {
    this(project, entry.getRootFiles(OrderRootType.CLASSES), entry.getRootFiles(OrderRootType.SOURCES), entry.getJdkName());
  }

  public JdkScope(Project project,
                  VirtualFile[] classes,
                  VirtualFile[] sources,
                  String jdkName) {
    super(project, classes, sources);
    myClasses = classes;
    mySources = sources;
    myJdkName = jdkName;
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(myClasses);
    result = 31 * result + Arrays.hashCode(mySources);
    result = 31 * result + myJdkName.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) return true;
    if (object.getClass() != getClass()) return false;

    final JdkScope that = (JdkScope)object;
    return myJdkName.equals(that.myJdkName) &&
           Arrays.equals(myClasses, that.myClasses) &&
           Arrays.equals(mySources, that.mySources);
  }
}
