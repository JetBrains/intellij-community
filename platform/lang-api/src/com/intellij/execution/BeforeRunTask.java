/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.execution;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.util.Key;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: May 18, 2009
 */
public abstract class BeforeRunTask<T extends BeforeRunTask> implements Cloneable {
  @NotNull
  protected final Key<T> myProviderId;

  // cannot be set to true by default, because RunManager.getHardcodedBeforeRunTasks creates before run task for each provider
  // and some providers set enabled to true in the constructor to indicate, that before run task should be added to RC by default (on create)
  private boolean myIsEnabled;

  protected BeforeRunTask(@NotNull Key<T> providerId) {
    myProviderId = providerId;
  }

  @NotNull
  public final Key<T> getProviderId() {
    return myProviderId;
  }

  public boolean isEnabled() {
    return myIsEnabled;
  }

  public void setEnabled(boolean isEnabled) {
    myIsEnabled = isEnabled;
  }

  public void writeExternal(@NotNull Element element) {
    if (this instanceof PersistentStateComponent) {
      ((PersistentStateComponent)this).getState();
    }
    else {
      element.setAttribute("enabled", String.valueOf(myIsEnabled));
    }
  }

  public void readExternal(@NotNull Element element) {
    String attribValue = element.getAttributeValue("enabled");
    if (attribValue == null) {
      attribValue = element.getAttributeValue("value"); // maintain compatibility with old format
    }
    myIsEnabled = attribValue == null || Boolean.parseBoolean(attribValue);
  }

  //Task may aggregate several items or targets to do (e.g. BuildArtifactsBeforeRunTask)
  public int getItemsCount() {
    return 1;
  }

  @Override
  public BeforeRunTask clone() {
    try {
      return (BeforeRunTask)super.clone();
    }
    catch (CloneNotSupportedException ignored) {
      return null;
    }
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BeforeRunTask that = (BeforeRunTask)o;
    if (myProviderId != that.myProviderId) return false;
    if (myIsEnabled != that.myIsEnabled) return false;

    return true;
  }

  public int hashCode() {
    return 31 * myProviderId.hashCode() + (myIsEnabled ? 1 : 0);
  }
}
