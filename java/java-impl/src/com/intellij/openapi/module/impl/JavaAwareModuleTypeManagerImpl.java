/*
 * User: anna
 * Date: 18-Feb-2008
 */
package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import org.jetbrains.annotations.NonNls;

public class JavaAwareModuleTypeManagerImpl extends ModuleTypeManagerImpl{
  @NonNls private static final String JAVA_MODULE_ID_OLD = "JAVA";

  public ModuleType getDefaultModuleType() {
    return StdModuleTypes.JAVA;
  }

  @Override
  public ModuleType findByID(final String moduleTypeID) {
    if (moduleTypeID != null) {
      if (JAVA_MODULE_ID_OLD.equals(moduleTypeID)) {
        return StdModuleTypes.JAVA; // for compatibility with the previous ID that Java modules had
      }
    }
    return super.findByID(moduleTypeID);
  }
}