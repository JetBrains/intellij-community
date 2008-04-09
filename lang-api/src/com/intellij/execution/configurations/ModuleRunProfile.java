package com.intellij.execution.configurations;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public interface ModuleRunProfile extends RunProfile {
  /**
   * @return modules to compile before run. Empty list to make project
   */
  @NotNull
  Module[] getModules();
}