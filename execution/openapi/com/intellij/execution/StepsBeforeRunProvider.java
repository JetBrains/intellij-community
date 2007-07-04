/*
 * Created by IntelliJ IDEA.
 * User: Vladislav.Kaznacheev
 * Date: Jul 4, 2007
 * Time: 12:33:18 AM
 */
package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;

public interface StepsBeforeRunProvider {
  @NonNls ExtensionPointName<StepsBeforeRunProvider> EXTENSION_POINT_NAME =
    new ExtensionPointName<StepsBeforeRunProvider>("com.intellij.stepsBeforeRunProvider");

  String getStepName();

  boolean hasTask(RunConfiguration configuration);

  boolean executeTask(DataContext context, RunConfiguration configuration);

  void copyTaskData(RunConfiguration from, RunConfiguration to);
}