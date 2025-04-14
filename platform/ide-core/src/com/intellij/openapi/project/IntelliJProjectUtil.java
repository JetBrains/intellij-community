// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.openapi.util.Key;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal;
import com.intellij.platform.workspace.jps.entities.ModuleId;
import com.intellij.platform.workspace.storage.CachedValue;
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class IntelliJProjectUtil {
  private static final Key<Boolean> IDEA_PROJECT = Key.create("idea.internal.inspections.enabled");
  private static final Key<Boolean> PLUGIN_PROJECT = Key.create("ij.platform.plugin.project");

  private static final String[] IDEA_PROJECT_MARKER_MODULE_NAMES = {
    // JPS module names
    "intellij.idea.community.main",
    "intellij.platform.commercial",
    "intellij.android.studio.integration",
    // Bazel module names
    "community.platform.platform-api.ide",
  };

  private final static CachedValue<Boolean> isIntelliJPlatformProjectValue = new CachedValue<>(storage -> {
    for (String ideaProjectMarkerName : IDEA_PROJECT_MARKER_MODULE_NAMES) {
      if (storage.resolve(new ModuleId(ideaProjectMarkerName)) != null) {
        return true;
      }
    }
    return false;
  });

  public static boolean isIntelliJPlatformProject(@Nullable Project project) {
    if (project == null) return false;
    VersionedEntityStorageImpl storage = ((WorkspaceModelInternal)WorkspaceModel.getInstance(project)).getEntityStorage();
    return storage.cachedValue(isIntelliJPlatformProjectValue) || Boolean.TRUE.equals(project.getUserData(IDEA_PROJECT));
  }

  public static boolean isIntelliJPluginProject(@Nullable Project project) {
    if (project == null) return false;

    var flag  = project.getUserData(PLUGIN_PROJECT);
    if (flag == null) {
      // `DevKitProjectTypeProvider.IDE_PLUGIN_PROJECT`
      flag = ContainerUtil.exists(ProjectTypeService.getProjectTypes(project), type -> "INTELLIJ_PLUGIN".equals(type.getId()));
      project.putUserData(PLUGIN_PROJECT, flag);
    }
    return flag;
  }

  @TestOnly
  public static void markAsIntelliJPlatformProject(@NotNull Project project, Boolean value) {
    project.putUserData(IDEA_PROJECT, value);
  }
}
