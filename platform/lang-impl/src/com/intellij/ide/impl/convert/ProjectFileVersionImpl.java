/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.impl.convert;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 *
 * DO NOT CONVERT THIS COMPONENT TO SERVICE. ITS CONFIGURATION IS ACCESSED VIA JDOM BEFORE PROJECT OPENING
 */
//todo[nik] remove
@State(
  name = ProjectFileVersionImpl.COMPONENT_NAME,
  storages = {
    @Storage(
      id="other",
      file = "$PROJECT_FILE$"
    )
  }
)
public class ProjectFileVersionImpl extends ProjectFileVersion implements ProjectComponent, PersistentStateComponent<ProjectFileVersionImpl.ProjectFileVersionState> {
  @NonNls public static final String COMPONENT_NAME = "ProjectFileVersion";

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return COMPONENT_NAME;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public ProjectFileVersionState getState() {
    return null;
  }

  public void loadState(final ProjectFileVersionState object) {
  }

  public static class ProjectFileVersionState {
  }
}
