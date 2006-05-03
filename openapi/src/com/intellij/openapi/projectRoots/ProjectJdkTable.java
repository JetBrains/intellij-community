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
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.application.ApplicationManager;

import java.util.EventListener;

public abstract class ProjectJdkTable {
  public static ProjectJdkTable getInstance() {
    return ApplicationManager.getApplication().getComponent(ProjectJdkTable.class);
  }

  public abstract ProjectJdk findJdk(String name);

  public abstract ProjectJdk findJdk(String name, String type);

  public abstract ProjectJdk getInternalJdk();

  public abstract ProjectJdk[] getAllJdks();

  public abstract void addJdk(ProjectJdk jdk);

  public abstract void removeJdk(ProjectJdk jdk);

  public abstract void updateJdk(ProjectJdk originalJdk, ProjectJdk modifiedJdk);

  public static interface Listener extends EventListener {
    void jdkAdded(ProjectJdk jdk);

    void jdkRemoved(ProjectJdk jdk);

    void jdkNameChanged(ProjectJdk jdk, String previousName);
  }

  public abstract void addListener(Listener listener);

  public abstract void removeListener(Listener listener);
}
