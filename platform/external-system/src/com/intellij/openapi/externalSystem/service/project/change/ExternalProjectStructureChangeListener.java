package com.intellij.openapi.externalSystem.service.project.change;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChange;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Defines common interface for clients interested in project structure change events.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 7:04 PM
 */
public interface ExternalProjectStructureChangeListener {

  /**
   * Notifies current listener on the newly discovered changes between the external system' and ide project models.
   * 
   * @param ideProject        target ide project
   * @param externalSystemId  if of the external system which project structure has been compared to the given ide project
   * @param oldChanges        changes between the external system' and ide project models that had been known prior to the current update
   * @param currentChanges    the most up-to-date changes between the external system and ide project models
   */
  void onChanges(@NotNull Project ideProject,
                 @NotNull ProjectSystemId externalSystemId,
                 @NotNull Collection<ExternalProjectStructureChange> oldChanges,
                 @NotNull Collection<ExternalProjectStructureChange> currentChanges);
}
