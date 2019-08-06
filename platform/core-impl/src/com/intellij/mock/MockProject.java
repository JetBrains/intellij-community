// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.picocontainer.PicoContainer;

/**
 * @author yole
 */
public class MockProject extends MockComponentManager implements Project {
  private static final Logger LOG = Logger.getInstance("#com.intellij.mock.MockProject");
  private VirtualFile myBaseDir;

  public MockProject(PicoContainer parent, @NotNull Disposable parentDisposable) {
    super(parent, parentDisposable);
  }

  @Override
  public boolean isDefault() {
    return false;
  }

  @NotNull
  @Override
  public Condition<?> getDisposed() {
    return o -> isDisposed();
  }

  @Override
  public boolean isOpen() {
    return false;
  }

  @Override
  public boolean isInitialized() {
    return true;
  }

  @Override
  public VirtualFile getProjectFile() {
    return null;
  }

  @Override
  @NotNull
  public String getName() {
    return "";
  }

  @Override
  @NotNull
  @NonNls
  public String getLocationHash() {
    return "mock";
  }

  @Override
  @Nullable
  @SystemIndependent
  public String getProjectFilePath() {
    return null;
  }

  @Override
  public VirtualFile getWorkspaceFile() {
    return null;
  }

  public void setBaseDir(VirtualFile baseDir) {
    myBaseDir = baseDir;
  }

  @Override
  @Nullable
  public VirtualFile getBaseDir() {
    return myBaseDir;
  }

  @Nullable
  @SystemIndependent
  @Override
  public String getBasePath() {
    return null;
  }

  @Override
  public void save() {
  }

  public void projectOpened() {
    final ProjectComponent[] components = getComponents(ProjectComponent.class);
    for (ProjectComponent component : components) {
      try {
        component.projectOpened();
      }
      catch (Throwable e) {
        LOG.error(component.toString(), e);
      }
    }
  }
}
