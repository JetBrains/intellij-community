// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.debugger.breakpoints.properties;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaExceptionBreakpointProperties extends JavaBreakpointProperties<JavaExceptionBreakpointProperties> {
  public boolean NOTIFY_CAUGHT = true;
  public boolean NOTIFY_UNCAUGHT = true;

  @Attribute("class")
  public @NlsSafe String myQualifiedName;

  @Attribute("package")
  public @NlsSafe String myPackageName;

  private boolean myCatchFiltersEnabled = false;
  private ClassFilter[] myCatchClassFilters;
  private ClassFilter[] myCatchClassExclusionFilters;

  /**
   * @deprecated use {@link #JavaExceptionBreakpointProperties(String)}
   */
  @Deprecated
  public JavaExceptionBreakpointProperties(String qualifiedName, String packageName) {
    myQualifiedName = qualifiedName;
    myPackageName = packageName;
  }

  public JavaExceptionBreakpointProperties(String qualifiedName) {
    myQualifiedName = qualifiedName;
    myPackageName = StringUtil.getPackageName(qualifiedName);
  }

  public JavaExceptionBreakpointProperties() {
  }

  @Override
  public @Nullable JavaExceptionBreakpointProperties getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull JavaExceptionBreakpointProperties state) {
    super.loadState(state);

    NOTIFY_CAUGHT = state.NOTIFY_CAUGHT;
    NOTIFY_UNCAUGHT = state.NOTIFY_UNCAUGHT;
    myQualifiedName = state.myQualifiedName;
    myPackageName = state.myPackageName;

    setCatchFiltersEnabled(state.isCatchFiltersEnabled());
    myCatchClassFilters = state.getCatchClassFilters();
    myCatchClassExclusionFilters = state.getCatchClassExclusionFilters();
  }

  @OptionTag("catch-filters-enabled")
  public boolean isCatchFiltersEnabled() {
    return myCatchFiltersEnabled;
  }

  public boolean setCatchFiltersEnabled(boolean enabled) {
    boolean changed = myCatchFiltersEnabled != enabled;
    myCatchFiltersEnabled = enabled;
    return changed;
  }

  @XCollection(propertyElementName = "catch-class-filters")
  public final ClassFilter[] getCatchClassFilters() {
    return myCatchClassFilters != null ? myCatchClassFilters : ClassFilter.EMPTY_ARRAY;
  }

  public final boolean setCatchClassFilters(ClassFilter[] classFilters) {
    boolean changed = !filtersEqual(myCatchClassFilters, classFilters);
    myCatchClassFilters = classFilters;
    return changed;
  }

  @XCollection(propertyElementName = "catch-class-exclusion-filters")
  public ClassFilter[] getCatchClassExclusionFilters() {
    return myCatchClassExclusionFilters != null ? myCatchClassExclusionFilters : ClassFilter.EMPTY_ARRAY;
  }

  public boolean setCatchClassExclusionFilters(ClassFilter[] classExclusionFilters) {
    boolean changed = !filtersEqual(myCatchClassExclusionFilters, classExclusionFilters);
    myCatchClassExclusionFilters = classExclusionFilters;
    return changed;
  }
}
