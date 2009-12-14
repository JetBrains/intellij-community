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
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.Condition;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;
import java.util.List;

public abstract class ProjectJdkTable {
  public static ProjectJdkTable getInstance() {
    return ServiceManager.getService(ProjectJdkTable.class);
  }

  @Nullable
  public abstract Sdk findJdk(String name);

  @Nullable
  public abstract Sdk findJdk(String name, String type);

  public abstract Sdk[] getAllJdks();

  public abstract List<Sdk> getSdksOfType(SdkType type);

  @Nullable
  public abstract Sdk findMostRecentSdkOfType(SdkType type);

  @Nullable
  public abstract Sdk findMostRecentSdk(Condition<Sdk> condition);

  public abstract void addJdk(Sdk jdk);

  public abstract void removeJdk(Sdk jdk);

  public abstract void updateJdk(Sdk originalJdk, Sdk modifiedJdk);

  public interface Listener extends EventListener {
    void jdkAdded(Sdk jdk);

    void jdkRemoved(Sdk jdk);

    void jdkNameChanged(Sdk jdk, String previousName);
  }

  /**
   * @deprecated use #JDK_TABLE_TOPIC instead
   */
  public abstract void addListener(Listener listener);

  /**
   * @deprecated use #JDK_TABLE_TOPIC instead
   */
  public abstract void removeListener(Listener listener);

  public abstract SdkType getDefaultSdkType();

  public abstract Sdk createSdk(final String name, final SdkType sdkType);

  public static Topic<Listener> JDK_TABLE_TOPIC = Topic.create("Project JDK table", Listener.class);
}
