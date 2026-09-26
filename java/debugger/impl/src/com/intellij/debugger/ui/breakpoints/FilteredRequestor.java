// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.engine.requests.LocatableEventRequestor;
import com.intellij.ui.classFilter.ClassFilter;

public interface FilteredRequestor extends LocatableEventRequestor {
  @Override
  String getSuspendPolicy();

  default boolean isInstanceFiltersEnabled() {
    return false;
  }

  default InstanceFilter[] getInstanceFilters() {
    return InstanceFilter.EMPTY_ARRAY;
  }

  default boolean isCountFilterEnabled() {
    return false;
  }

  default int getCountFilter() {
    return 0;
  }

  default boolean isClassFiltersEnabled() {
    return false;
  }

  default ClassFilter[] getClassFilters() {
    return ClassFilter.EMPTY_ARRAY;
  }

  default ClassFilter[] getClassExclusionFilters() {
    return ClassFilter.EMPTY_ARRAY;
  }

  default boolean isConditionEnabled() {
    return false;
  }
}
