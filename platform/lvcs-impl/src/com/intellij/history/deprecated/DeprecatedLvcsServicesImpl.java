package com.intellij.history.deprecated;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.localVcs.LocalVcsItemsLocker;
import com.intellij.openapi.localVcs.LocalVcsServices;
import com.intellij.openapi.localVcs.LvcsRevision;
import org.jetbrains.annotations.NotNull;

public class DeprecatedLvcsServicesImpl extends LocalVcsServices implements ProjectComponent {
  public LocalVcsItemsLocker getUpToDateRevisionProvider() {
    return new LocalVcsItemsLocker() {
      public boolean itemCanBePurged(final LvcsRevision lvcsObject) {
        return true;
      }
    };
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "LvcsBasedUpToDateVersionProvider";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
