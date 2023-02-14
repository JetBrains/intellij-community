// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.debugger.breakpoints.properties;

import com.intellij.debugger.InstanceFilter;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class JavaBreakpointProperties<T extends JavaBreakpointProperties> extends XBreakpointProperties<T> {
  private boolean COUNT_FILTER_ENABLED = false;
  private int COUNT_FILTER = 0;

  private boolean CLASS_FILTERS_ENABLED = false;
  private ClassFilter[] myClassFilters;
  private ClassFilter[] myClassExclusionFilters;

  private boolean INSTANCE_FILTERS_ENABLED = false;
  private InstanceFilter[] myInstanceFilters;

  private boolean CALLER_FILTERS_ENABLED = false;
  private ClassFilter[] myCallerFilters;
  private ClassFilter[] myCallerExclusionFilters;

  private boolean TRACING_START = false;
  private boolean TRACING_END = false;

  @XCollection(propertyElementName = "instance-filters")
  public InstanceFilter[] getInstanceFilters() {
    return myInstanceFilters != null ? myInstanceFilters : InstanceFilter.EMPTY_ARRAY;
  }

  public boolean setInstanceFilters(InstanceFilter[] instanceFilters) {
    boolean changed = !filtersEqual(myInstanceFilters, instanceFilters);
    myInstanceFilters = instanceFilters;
    return changed;
  }

  public void addInstanceFilter(long l) {
    InstanceFilter newFilter = InstanceFilter.create(l);
    if (myInstanceFilters == null) {
      myInstanceFilters = new InstanceFilter[]{newFilter};
    }
    else {
      myInstanceFilters = ArrayUtil.append(myInstanceFilters, newFilter);
    }
  }

  @XCollection(propertyElementName = "class-filters")
  public final ClassFilter[] getClassFilters() {
    return myClassFilters != null ? myClassFilters : ClassFilter.EMPTY_ARRAY;
  }

  public final boolean setClassFilters(ClassFilter[] classFilters) {
    boolean changed = !filtersEqual(myClassFilters, classFilters);
    myClassFilters = classFilters;
    return changed;
  }

  protected static boolean filtersEqual(Object[] a, Object[] b) {
    if ((a == null || a.length == 0) && (b == null || b.length == 0)) {
      return true;
    }
    return Arrays.equals(a, b);
  }

  @XCollection(propertyElementName = "class-exclusion-filters")
  public ClassFilter[] getClassExclusionFilters() {
    return myClassExclusionFilters != null ? myClassExclusionFilters : ClassFilter.EMPTY_ARRAY;
  }

  public boolean setClassExclusionFilters(ClassFilter[] classExclusionFilters) {
    boolean changed = !filtersEqual(myClassExclusionFilters, classExclusionFilters);
    myClassExclusionFilters = classExclusionFilters;
    return changed;
  }

  @Nullable
  @Override
  public T getState() {
    return (T)this;
  }

  @Override
  public void loadState(@NotNull T state) {
    setCOUNT_FILTER_ENABLED(state.isCOUNT_FILTER_ENABLED());
    setCOUNT_FILTER(state.getCOUNT_FILTER());

    setCLASS_FILTERS_ENABLED(state.isCLASS_FILTERS_ENABLED());
    myClassFilters = state.getClassFilters();
    myClassExclusionFilters = state.getClassExclusionFilters();

    setINSTANCE_FILTERS_ENABLED(state.isINSTANCE_FILTERS_ENABLED());
    myInstanceFilters = state.getInstanceFilters();

    setCALLER_FILTERS_ENABLED(state.isCALLER_FILTERS_ENABLED());
    myCallerFilters = state.getCallerFilters();
    myCallerExclusionFilters = state.getCallerExclusionFilters();

    setTRACING_START(state.isTRACING_START());
    setTRACING_END(state.isTRACING_END());
  }

  @OptionTag("count-filter-enabled")
  public boolean isCOUNT_FILTER_ENABLED() {
    return COUNT_FILTER_ENABLED;
  }

  public boolean setCOUNT_FILTER_ENABLED(boolean COUNT_FILTER_ENABLED) {
    boolean changed = this.COUNT_FILTER_ENABLED != COUNT_FILTER_ENABLED;
    this.COUNT_FILTER_ENABLED = COUNT_FILTER_ENABLED;
    return changed;
  }

  @OptionTag("count-filter")
  public int getCOUNT_FILTER() {
    return COUNT_FILTER;
  }

  public boolean setCOUNT_FILTER(int COUNT_FILTER) {
    boolean changed = this.COUNT_FILTER != COUNT_FILTER;
    this.COUNT_FILTER = COUNT_FILTER;
    return changed;
  }

  @OptionTag("class-filters-enabled")
  public boolean isCLASS_FILTERS_ENABLED() {
    return CLASS_FILTERS_ENABLED;
  }

  public boolean setCLASS_FILTERS_ENABLED(boolean CLASS_FILTERS_ENABLED) {
    boolean changed = this.CLASS_FILTERS_ENABLED != CLASS_FILTERS_ENABLED;
    this.CLASS_FILTERS_ENABLED = CLASS_FILTERS_ENABLED;
    return changed;
  }

  @OptionTag("instance-filters-enabled")
  public boolean isINSTANCE_FILTERS_ENABLED() {
    return INSTANCE_FILTERS_ENABLED;
  }

  public boolean setINSTANCE_FILTERS_ENABLED(boolean INSTANCE_FILTERS_ENABLED) {
    boolean changed = this.INSTANCE_FILTERS_ENABLED != INSTANCE_FILTERS_ENABLED;
    this.INSTANCE_FILTERS_ENABLED = INSTANCE_FILTERS_ENABLED;
    return changed;
  }

  @OptionTag("caller-filters-enabled")
  public boolean isCALLER_FILTERS_ENABLED() {
    return CALLER_FILTERS_ENABLED;
  }

  public boolean setCALLER_FILTERS_ENABLED(boolean CALLER_FILTERS_ENABLED) {
    boolean changed = this.CALLER_FILTERS_ENABLED != CALLER_FILTERS_ENABLED;
    this.CALLER_FILTERS_ENABLED = CALLER_FILTERS_ENABLED;
    return changed;
  }

  @XCollection(propertyElementName = "caller-filters")
  public ClassFilter[] getCallerFilters() {
    return myCallerFilters != null ? myCallerFilters : ClassFilter.EMPTY_ARRAY;
  }

  public boolean setCallerFilters(ClassFilter[] callerFilters) {
    boolean changed = !filtersEqual(myCallerFilters, callerFilters);
    myCallerFilters = callerFilters;
    return changed;
  }

  @XCollection(propertyElementName = "caller-exclusion-filters")
  public ClassFilter[] getCallerExclusionFilters() {
    return myCallerExclusionFilters != null ? myCallerExclusionFilters : ClassFilter.EMPTY_ARRAY;
  }

  public boolean setCallerExclusionFilters(ClassFilter[] callerExclusionFilters) {
    boolean changed = !filtersEqual(myCallerExclusionFilters, callerExclusionFilters);
    myCallerExclusionFilters = callerExclusionFilters;
    return changed;
  }

  @OptionTag("tracing-start")
  public boolean isTRACING_START() {
    return TRACING_START;
  }

  public boolean setTRACING_START(boolean TRACING_START) {
    boolean changed = this.TRACING_START != TRACING_START;
    this.TRACING_START = TRACING_START;
    return changed;
  }

  @OptionTag("tracing-end")
  public boolean isTRACING_END() {
    return TRACING_END;
  }

  public boolean setTRACING_END(boolean TRACING_END) {
    boolean changed = this.TRACING_END != TRACING_END;
    this.TRACING_END = TRACING_END;
    return changed;
  }
}
