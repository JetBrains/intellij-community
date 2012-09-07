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

/**
 * @author max
 */
public class JdkScope extends LibraryScopeBase {
  private final String myJdkName;

  public JdkScope(Project project, JdkOrderEntry jdk) {
    super(project, jdk.getRootFiles(OrderRootType.CLASSES), jdk.getRootFiles(OrderRootType.SOURCES));
    myJdkName = jdk.getJdkName();
  }

  public int hashCode() {
    return myJdkName.hashCode();
  }

  public boolean equals(Object object) {
    if (object == this) return true;
    if (object == null) return false;
    if (object.getClass() != JdkScope.class) return false;

    final JdkScope that = (JdkScope)object;
    return that.myJdkName.equals(myJdkName);
  }
}
