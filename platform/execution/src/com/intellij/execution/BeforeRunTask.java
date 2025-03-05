// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.util.Key;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public abstract class BeforeRunTask<T extends BeforeRunTask<?>> implements Cloneable {
  protected final @NotNull Key<T> myProviderId;

  // cannot be set to true by default, because RunManager.getHardcodedBeforeRunTasks creates before run task for each provider
  // and some providers set enabled to true in the constructor to indicate, that before run task should be added to RC by default (on create)
  private boolean myIsEnabled;

  protected BeforeRunTask(@NotNull Key<T> providerId) {
    myProviderId = providerId;
  }

  public final @NotNull Key<T> getProviderId() {
    return myProviderId;
  }

  public boolean isEnabled() {
    return myIsEnabled;
  }

  public void setEnabled(boolean isEnabled) {
    myIsEnabled = isEnabled;
  }

  /**
   * @deprecated Use {@link PersistentStateComponent} instead (see {@link com.intellij.ide.browsers.LaunchBrowserBeforeRunTask} for example).
   */
  @Deprecated
  public void writeExternal(@NotNull Element element) {
    if (this instanceof PersistentStateComponent) {
      ((PersistentStateComponent<?>)this).getState();
    }
    else {
      element.setAttribute("enabled", String.valueOf(myIsEnabled));
    }
  }

  /**
   * @deprecated Use {@link PersistentStateComponent} instead (see {@link com.intellij.ide.browsers.LaunchBrowserBeforeRunTask} for example).
   */
  @Deprecated
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BeforeRunTask<?> that = (BeforeRunTask<?>)o;
    if (myProviderId != that.myProviderId) return false;
    if (myIsEnabled != that.myIsEnabled) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return 31 * myProviderId.hashCode() + (myIsEnabled ? 1 : 0);
  }
}
