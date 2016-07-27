/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.java.debugger.breakpoints.properties;

import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * @author egor
 */
public class JavaExceptionBreakpointProperties extends JavaBreakpointProperties<JavaExceptionBreakpointProperties> {
  public boolean NOTIFY_CAUGHT   = true;
  public boolean NOTIFY_UNCAUGHT = true;

  @Attribute("class")
  public String myQualifiedName;

  @Attribute("package")
  public String myPackageName;

  private boolean myCatchFiltersEnabled = false;
  private ClassFilter[] myCatchClassFilters;
  private ClassFilter[] myCatchClassExclusionFilters;

  public JavaExceptionBreakpointProperties(String qualifiedName, String packageName) {
    myQualifiedName = qualifiedName;
    myPackageName = packageName;
  }

  public JavaExceptionBreakpointProperties() {
  }

  @Nullable
  @Override
  public JavaExceptionBreakpointProperties getState() {
    return this;
  }

  @Override
  public void loadState(JavaExceptionBreakpointProperties state) {
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

  @Tag("catch-class-filters")
  @AbstractCollection(surroundWithTag = false)
  public final ClassFilter[] getCatchClassFilters() {
    return myCatchClassFilters != null ? myCatchClassFilters : ClassFilter.EMPTY_ARRAY;
  }

  public final boolean setCatchClassFilters(ClassFilter[] classFilters) {
    boolean changed = !filtersEqual(myCatchClassFilters, classFilters);
    myCatchClassFilters = classFilters;
    return changed;
  }

  @Tag("catch-class-exclusion-filters")
  @AbstractCollection(surroundWithTag = false)
  public ClassFilter[] getCatchClassExclusionFilters() {
    return myCatchClassExclusionFilters != null ? myCatchClassExclusionFilters : ClassFilter.EMPTY_ARRAY;
  }

  public boolean setCatchClassExclusionFilters(ClassFilter[] classExclusionFilters) {
    boolean changed = !filtersEqual(myCatchClassExclusionFilters, classExclusionFilters);
    myCatchClassExclusionFilters = classExclusionFilters;
    return changed;
  }
}
