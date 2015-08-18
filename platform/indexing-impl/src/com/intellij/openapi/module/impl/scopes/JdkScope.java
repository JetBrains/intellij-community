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

/**
 * @author max
 */
public class JdkScope extends LibraryScopeBase {
  private final String myJdkName;

  public JdkScope(Project project, JdkOrderEntry entry) {
    this(project, entry.getRootFiles(OrderRootType.CLASSES), entry.getRootFiles(OrderRootType.SOURCES), entry.getJdkName());
  }

  public JdkScope(Project project,
                  VirtualFile[] classes,
                  VirtualFile[] sources,
                  String jdkName) {
    super(project, classes, sources);
    myJdkName = jdkName;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + myJdkName.hashCode();
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) return true;
    if (object.getClass() != getClass()) return false;

    return myJdkName.equals(((JdkScope)object).myJdkName) && super.equals(object);
  }
}
