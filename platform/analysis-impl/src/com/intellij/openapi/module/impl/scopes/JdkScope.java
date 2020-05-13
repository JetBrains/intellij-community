// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;

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
  public int calcHashCode() {
    return 31 * super.calcHashCode() + myJdkName.hashCode();
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) return true;
    if (object.getClass() != getClass()) return false;

    return myJdkName.equals(((JdkScope)object).myJdkName) && super.equals(object);
  }
}
