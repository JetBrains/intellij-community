/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInspection;

/**
 * Plugin's ApplicationComponent that implements this interface will be automatically queried for inspection tool classes.
 */
public interface InspectionToolProvider {
  /**
   * Query method for inspection tools provided by a plugin.
   * @return classes that extend {@link LocalInspectionTool}
   */
  Class[] getInspectionClasses();
}
