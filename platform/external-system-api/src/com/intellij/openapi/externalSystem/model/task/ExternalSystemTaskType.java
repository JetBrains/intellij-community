package com.intellij.openapi.externalSystem.model.task;

/**
 * Enumerates interested types of tasks that may be enqueued to external systems API.
 */
public enum ExternalSystemTaskType {
  
  RESOLVE_PROJECT, REFRESH_TASKS_LIST, EXECUTE_TASK
}
