package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * Defines contract for the callback to be notified on project structure changes triggered by external system integrations.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/24/12 1:05 PM
 */
public interface ProjectEntityChangeListener {

  Topic<ProjectEntityChangeListener> TOPIC = Topic.create("External system project change", ProjectEntityChangeListener.class);

  /**
   * Is called <b>before</b> the given entity is changed.
   *
   * @param entity            target entity being changed
   * @param externalSystemId  id of the target external system which integration triggered project change
   */
  void onChangeStart(@NotNull Object entity, @NotNull ProjectSystemId externalSystemId);

  /**
   * Is called <b>after</b> the given entity has been changed.
   *
   * @param entity            target entity that has been changed
   * @param externalSystemId  id of the target external system which integration triggered project change
   */
  void onChangeEnd(@NotNull Object entity, @NotNull ProjectSystemId externalSystemId);
}
