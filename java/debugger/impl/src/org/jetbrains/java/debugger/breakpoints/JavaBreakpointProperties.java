/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.debugger.breakpoints;

import com.intellij.debugger.InstanceFilter;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import org.jetbrains.annotations.Nullable;

/**
 * @author egor
 */
public class JavaBreakpointProperties extends XBreakpointProperties<JavaBreakpointProperties> {
  public boolean COUNT_FILTER_ENABLED     = false;
  public int COUNT_FILTER = 0;

  public boolean CLASS_FILTERS_ENABLED    = false;
  private ClassFilter[] myClassFilters;
  private ClassFilter[] myClassExclusionFilters;

  public boolean INSTANCE_FILTERS_ENABLED = false;
  private InstanceFilter[] myInstanceFilters;

  public InstanceFilter[] getInstanceFilters() {
    return myInstanceFilters != null ? myInstanceFilters : InstanceFilter.EMPTY_ARRAY;
  }

  public void setInstanceFilters(InstanceFilter[] instanceFilters) {
    myInstanceFilters = instanceFilters;
  }

  protected void addInstanceFilter(long l) {
    final InstanceFilter[] filters = new InstanceFilter[myInstanceFilters.length + 1];
    System.arraycopy(myInstanceFilters, 0, filters, 0, myInstanceFilters.length);
    filters[myInstanceFilters.length] = InstanceFilter.create(String.valueOf(l));
    myInstanceFilters = filters;
  }

  public final ClassFilter[] getClassFilters() {
    return myClassFilters != null ? myClassFilters : ClassFilter.EMPTY_ARRAY;
  }

  public final void setClassFilters(ClassFilter[] classFilters) {
    myClassFilters = classFilters;
  }

  public ClassFilter[] getClassExclusionFilters() {
    return myClassExclusionFilters != null ? myClassExclusionFilters : ClassFilter.EMPTY_ARRAY;
  }

  public void setClassExclusionFilters(ClassFilter[] classExclusionFilters) {
    myClassExclusionFilters = classExclusionFilters;
  }

  @Nullable
  @Override
  public JavaBreakpointProperties getState() {
    return this;
  }

  @Override
  public void loadState(JavaBreakpointProperties state) {
    COUNT_FILTER_ENABLED = state.COUNT_FILTER_ENABLED;
    COUNT_FILTER = state.COUNT_FILTER;

    CLASS_FILTERS_ENABLED = state.CLASS_FILTERS_ENABLED;
    myClassFilters = state.myClassFilters;
    myClassExclusionFilters = state.myClassExclusionFilters;

    INSTANCE_FILTERS_ENABLED = state.INSTANCE_FILTERS_ENABLED;
    myInstanceFilters = state.myInstanceFilters;
  }
}
