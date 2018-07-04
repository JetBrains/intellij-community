package com.intellij.compiler.server;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;

import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 */
public interface BuildManagerListener {
  Topic<BuildManagerListener> TOPIC = Topic.create("Build Manager", BuildManagerListener.class);

  default void beforeBuildProcessStarted(Project project, UUID sessionId) {}

  default void buildStarted(Project project, UUID sessionId, boolean isAutomake) {}

  default void buildFinished(Project project, UUID sessionId, boolean isAutomake) {}
}
