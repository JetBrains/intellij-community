// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger;

import org.jetbrains.annotations.NonNls;

public interface HelpID {
  @NonNls String LINE_BREAKPOINTS = "debugging.lineBreakpoint";
  @NonNls String METHOD_BREAKPOINTS = "debugging.methodBreakpoint";
  @NonNls String EXCEPTION_BREAKPOINTS = "debugging.exceptionBreakpoint";
  @NonNls String FIELD_WATCHPOINTS = "debugging.fieldWatchpoint";
  @NonNls String COLLECTION_WATCHPOINTS = "debugging.collectionWatchpoint";
  @NonNls String EVALUATE = "debugging.debugMenu.evaluate";
  @NonNls String EXPORT_THREADS = "reference.run.export.thread";
}
