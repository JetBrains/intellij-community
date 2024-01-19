// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server.impl;

import com.intellij.compiler.server.BuildManager;
import com.intellij.java.workspace.entities.JavaModuleSettingsEntity;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.jps.entities.SourceRootEntity;
import com.intellij.platform.workspace.storage.EntityChange;
import com.intellij.platform.workspace.storage.VersionedStorageChange;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

public final class BuildProcessPreloadedStateClearer implements WorkspaceModelChangeListener {

  private final Project myProject;

  public BuildProcessPreloadedStateClearer(Project project) {
    myProject = project;
  }

  @Override
  public void changed(@NotNull VersionedStorageChange event) {
    boolean needFSRescan =
      processEntityChanges(event, SourceRootEntity.class, ChangeProcessor.anyChange(true, false)) ||
      processEntityChanges(event, ModuleEntity.class, (before, after) -> before.getDependencies().equals(after.getDependencies()),
                           ChangeProcessor.anyChange(true, false)) ||
      processEntityChanges(event, JavaModuleSettingsEntity.class, ChangeProcessor.anyChange(true, false));

    if (needFSRescan) {
      BuildManager.getInstance().clearState(myProject);
    }
    else if (event.getAllChanges().iterator().hasNext()) {
      BuildManager.getInstance().cancelPreloadedBuilds(myProject);
    }
  }

  interface ChangeProcessor<T, R> {
    default boolean added(T newData) {
      return true;
    }

    default boolean changed(T oldData, T newData) {
      return true;
    }

    default boolean removed(T oldData) {
      return true;
    }

    default R getResult() {
      return null;
    }

    static <T, R> ChangeProcessor<T, R> anyChange(R onChangesDetected, R noChanges) {
      return new ChangeProcessor<>() {
        private R myResult = noChanges;

        @Override
        public boolean added(Object newEntity) {
          myResult = onChangesDetected;
          return false;
        }

        @Override
        public boolean changed(Object oldEntity, Object newEntity) {
          myResult = onChangesDetected;
          return false;
        }

        @Override
        public boolean removed(Object oldEntity) {
          myResult = onChangesDetected;
          return false;
        }

        @Override
        public R getResult() {
          return myResult;
        }
      };
    }
  }

  private static <T extends WorkspaceEntity, R> R processEntityChanges(@NotNull VersionedStorageChange event,
                                                                       Class<T> entityClass,
                                                                       ChangeProcessor<T, R> proc) {
    return processEntityChanges(event, entityClass, Object::equals, proc);
  }

  private static <T extends WorkspaceEntity, R> R processEntityChanges(@NotNull VersionedStorageChange event,
                                                                       Class<T> entityClass,
                                                                       BiFunction<T, T, Boolean> equalsBy,
                                                                       ChangeProcessor<T, R> proc) {
    for (EntityChange<T> change : event.getChanges(entityClass)) {
      final T before = change.getOldEntity();
      final T after = change.getNewEntity();
      boolean shouldContinue = true;
      if (after != null) {
        if (before != null) {
          if (!equalsBy.apply(before, after)) {
            shouldContinue = proc.changed(before, after);
          }
        }
        else {
          shouldContinue = proc.added(after);
        }
      }
      else if (before != null) {
        shouldContinue = proc.removed(before);
      }
      if (!shouldContinue) {
        return proc.getResult();
      }
    }
    return proc.getResult();
  }
}
