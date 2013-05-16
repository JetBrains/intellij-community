package com.intellij.openapi.externalSystem.model;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.externalSystem.service.task.ExternalSystemTasksTreeModel;

/**
 * @author Denis Zhdanov
 * @since 2/7/12 11:19 AM
 */
public class ExternalSystemDataKeys {

  public static final DataKey<ExternalSystemTasksTreeModel> ALL_TASKS_MODEL = DataKey.create("external.system.all.tasks.model");

  private ExternalSystemDataKeys() {
  }
}
