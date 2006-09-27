/*
 * @author: Eugene Zhuravlev
 * Date: Nov 12, 2002
 * Time: 3:25:10 PM
 */
package com.intellij.debugger;

import org.jetbrains.annotations.NonNls;

public interface HelpID {
  @NonNls String LINE_BREAKPOINTS = "debugging.lineBreakpoint";
  @NonNls String METHOD_BREAKPOINTS = "debugging.methodBreakpoint";
  @NonNls String EXCEPTION_BREAKPOINTS = "debugging.exceptionBreakpoint";
  @NonNls String FIELD_WATCHPOINTS = "debugging.fieldWatchpoint";
  @NonNls String EVALUATE = "debugging.debugMenu.evaluate";
}
