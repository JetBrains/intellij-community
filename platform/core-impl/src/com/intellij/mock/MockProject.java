// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.diagnostic.ActivityCategory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.ComponentManagerEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.util.coroutines.CoroutineScopeKt;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.picocontainer.PicoContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

public class MockProject extends MockComponentManager implements Project, ComponentManagerEx {
  private static final Logger LOG = Logger.getInstance(MockProject.class);
  private VirtualFile myBaseDir;
  private final CoroutineScope myCoroutineScope;

  @Internal
  public MockProject(@Nullable PicoContainer parent, @NotNull Disposable parentDisposable) {
    super(parent, parentDisposable);

    myCoroutineScope = CoroutineScopeKt.childScope(GlobalScope.INSTANCE, "MockProject: " + this,
                                                   EmptyCoroutineContext.INSTANCE, true);
  }

  @Override
  public void dispose() {
    kotlinx.coroutines.CoroutineScopeKt.cancel(myCoroutineScope, new CancellationException());

    super.dispose();
  }

  @Override
  public @NotNull Condition<?> getDisposed() {
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
  public @NotNull CoroutineScope getCoroutineScope() {
    return myCoroutineScope;
  }

  @Override
  public ComponentManager getActualComponentManager() {
    return this;
  }

  @Override
  public VirtualFile getProjectFile() {
    return null;
  }

  @Override
  public @NotNull String getName() {
    return "";
  }

  @Override
  public @NotNull @NonNls String getLocationHash() {
    return "mock";
  }

  @Override
  public @Nullable @SystemIndependent String getProjectFilePath() {
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
  public @Nullable VirtualFile getBaseDir() {
    return myBaseDir;
  }

  @Override
  public @Nullable @SystemIndependent String getBasePath() {
    return null;
  }

  @Override
  public void save() {
  }

  public @NotNull <T> List<T> getComponentInstancesOfType(@NotNull Class<T> componentType) {
    List<T> result = new ArrayList<>();
    getPicoContainer().getComponentAdapters().forEach(componentAdapter -> {
      Class<?> descendant = componentAdapter.getComponentImplementation();
      if (componentType == descendant || componentType.isAssignableFrom(descendant)) {
        //noinspection unchecked
        T instance = (T)componentAdapter.getComponentInstance();
        // may be null in the case of the "implicit" adapter representing "this"
        if (instance != null) {
          result.add(instance);
        }
      }
    });
    return result;
  }

  public void projectOpened() {
    for (ProjectComponent component : getComponentInstancesOfType(ProjectComponent.class)) {
      try {
        component.projectOpened();
      }
      catch (Throwable e) {
        LOG.error(component.toString(), e);
      }
    }
  }

  @Override
  public final @NotNull ActivityCategory getActivityCategory(boolean isExtension) {
    return isExtension ? ActivityCategory.PROJECT_EXTENSION : ActivityCategory.PROJECT_SERVICE;
  }
}
