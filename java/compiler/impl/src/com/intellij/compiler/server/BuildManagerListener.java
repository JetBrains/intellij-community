package com.intellij.compiler.server;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;

import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 7/3/13
 */
public interface BuildManagerListener {
  Topic<BuildManagerListener> TOPIC = Topic.create("Build Manager", BuildManagerListener.class);
  
  void beforeBuildProcessStarted(Project project, UUID sessionId);
  
  void buildStarted(Project project, UUID sessionId, boolean isAutomake);
  
  void buildFinished(Project project, UUID sessionId, boolean isAutomake);
}
