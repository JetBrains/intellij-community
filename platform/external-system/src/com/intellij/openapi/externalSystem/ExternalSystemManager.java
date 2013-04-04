package com.intellij.openapi.externalSystem;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

/**
 * IntelliJ external systems integration is built using GoF Bridge pattern, i.e. 'external-system' module defines
 * external system-specific extension (current interface) and an api which is used by all extensions. Most of the codebase
 * is built on top of that api and provides generic actions like 'sync ide project with external project'; 'import library
 * dependencies which are configured at external system but not at the ide' etc.
 * <p/>
 * That makes it relatively easy to add a new external system integration.
 * 
 * @author Denis Zhdanov
 * @since 4/4/13 4:05 PM
 */
public interface ExternalSystemManager {

  /**
   * @return    id of the external system represented by the current manager
   */
  @NotNull
  ProjectSystemId getSystemId();

  /**
   * @return    a strategy which can be queried for external system settings to use with the given project
   */
  @NotNull
  Function<Project, ? extends AbstractExternalSystemSettings<?, ?>> getSettingsProvider();

  /**
   * @return    a strategy which can be queried for external system local settings to use with the given project
   */
  @NotNull
  Function<Project, ? extends AbstractExternalSystemLocalSettings<?>> getLocalSettingsProvider();
}
