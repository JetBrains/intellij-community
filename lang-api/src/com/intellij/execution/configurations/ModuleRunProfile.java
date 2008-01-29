package com.intellij.execution.configurations;

import com.intellij.openapi.module.Module;

/**
 * @author spleaner
 */
public interface ModuleRunProfile extends RunProfile {
  // return modules to compile before run. Null or empty list to make project
  Module[] getModules();
}
