// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.autoimport;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.externalSystem.util.ExternalSystemLoggerUtilKt.debugTrace;


/// @deprecated use {@link ExternalSystemProjectTracker} instead
@ApiStatus.Internal
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public class ExternalSystemProjectsWatcherImpl implements ExternalSystemProjectsWatcher {

  private final static @NotNull Logger LOG = Logger.getInstance("#com.intellij.openapi.externalSystem.autoimport");

  private final @NotNull Project project;

  private static final ExtensionPointName<Contributor> EP_NAME =
    ExtensionPointName.create("com.intellij.externalProjectWatcherContributor");

  public ExternalSystemProjectsWatcherImpl(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public void markDirtyAllExternalProjects() {
    debugTrace(LOG, "Mark Dirty All External Projects");
    ExternalSystemProjectTracker projectTracker = ExternalSystemProjectTracker.getInstance(project);
    whenSettingsLoaded(() -> {
      findAllProjectSettings().forEach(it -> projectTracker.markDirty(it));
      for (Contributor contributor : EP_NAME.getExtensions()) {
        contributor.markDirtyAllExternalProjects(project);
      }
    });
  }

  @Override
  public void markDirty(@NotNull Module module) {
    debugTrace(LOG, "Module (%s): Mark Dirty External Project".formatted(module.getName()));
    String projectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
    ExternalSystemProjectTracker projectTracker = ExternalSystemProjectTracker.getInstance(project);
    whenSettingsLoaded(() -> {
      findAllProjectSettings().stream()
        .filter(it -> it.getExternalProjectPath().equals(projectPath))
        .forEach(it -> projectTracker.markDirty(it));
      for (Contributor contributor : EP_NAME.getExtensions()) {
        contributor.markDirty(module);
      }
    });
  }

  @Override
  public void markDirty(@NotNull String projectPath) {
    debugTrace(LOG, "Project (%s): Mark Dirty External Project".formatted(PathUtil.getFileName(projectPath)));
    ExternalSystemProjectTracker projectTracker = ExternalSystemProjectTracker.getInstance(project);
    whenSettingsLoaded(() -> {
      findAllProjectSettings().stream()
        .filter(it -> it.getExternalProjectPath().equals(projectPath))
        .forEach(it -> projectTracker.markDirty(it));
      for (Contributor contributor : EP_NAME.getExtensions()) {
        contributor.markDirty(projectPath);
      }
    });
  }

  private List<ExternalSystemProjectId> findAllProjectSettings() {
    List<ExternalSystemProjectId> list = new ArrayList<>();
    ExternalSystemManager.EP_NAME.forEachExtensionSafe(manager -> {
      ProjectSystemId systemId = manager.getSystemId();
      Collection<? extends ExternalProjectSettings> linkedProjectsSettings =
        manager.getSettingsProvider().fun(project).getLinkedProjectsSettings();
      for (ExternalProjectSettings settings : linkedProjectsSettings) {
        String externalProjectPath = settings.getExternalProjectPath();
        if (externalProjectPath == null) continue;
        list.add(new ExternalSystemProjectId(systemId, externalProjectPath));
      }
    });
    return list;
  }

  /**
   * Executes {@param runnable} when AbstractExternalSystemSettings is loaded.
   * <p>
   * Cannot use {@link com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager#runWhenInitialized},
   * because {@link ExternalSystemProjectsWatcher} is used in {@link  ExternalProjectsManagerImpl#init}.
   *
   * @see com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings#loadState
   */
  private void whenSettingsLoaded(@NotNull Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(runnable, project.getDisposed());
  }

  /**
   * @deprecated see {@link ExternalSystemProjectsWatcherImpl}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public interface Contributor {

    void markDirtyAllExternalProjects(@NotNull Project project);

    void markDirty(@NotNull Module module);

    default void markDirty(@NotNull String projectPath) {}
  }
}
