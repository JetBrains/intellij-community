// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * Allows to filter out more specific places, not whole classes like in {@link com.intellij.ui.classFilter.DebuggerClassFilterProvider}
 */
public interface ExtraSteppingFilter {
  ExtensionPointName<ExtraSteppingFilter> EP_NAME = ExtensionPointName.create("com.intellij.debugger.extraSteppingFilter");

  boolean isApplicable(SuspendContext context);

  /**@return Step request depth as defined in {@link com.sun.jdi.request.StepRequest}
   */
  int getStepRequestDepth(SuspendContext context);
}
