// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.classFilter;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.sun.jdi.request.StepRequest;

import java.util.List;

/**
 * Allows providing custom filters for step requests
 * @see StepRequest#addClassExclusionFilter(String)
 */
public interface DebuggerClassFilterProvider {
  ExtensionPointName<DebuggerClassFilterProvider> EP_NAME = new ExtensionPointName<>("com.intellij.debuggerClassFilterProvider");

  List<ClassFilter> getFilters();
}
