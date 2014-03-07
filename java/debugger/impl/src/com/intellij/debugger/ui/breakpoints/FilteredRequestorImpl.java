/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * class FilteredRequestorImpl
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiElement;
import com.intellij.ui.classFilter.ClassFilter;
import com.sun.jdi.event.LocatableEvent;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/*
 * Not used any more, since move to xBreakpoints
 */
public class FilteredRequestorImpl implements JDOMExternalizable, FilteredRequestor {

  public String  SUSPEND_POLICY = DebuggerSettings.SUSPEND_ALL;
  public boolean  SUSPEND = true;

  public boolean COUNT_FILTER_ENABLED     = false;
  public int COUNT_FILTER = 0;

  public boolean CONDITION_ENABLED        = false;
  private TextWithImports myCondition;

  public boolean CLASS_FILTERS_ENABLED    = false;
  private ClassFilter[] myClassFilters          = ClassFilter.EMPTY_ARRAY;
  private ClassFilter[] myClassExclusionFilters = ClassFilter.EMPTY_ARRAY;

  public boolean INSTANCE_FILTERS_ENABLED = false;
  private InstanceFilter[] myInstanceFilters  = InstanceFilter.EMPTY_ARRAY;

  @NonNls private static final String FILTER_OPTION_NAME = "filter";
  @NonNls private static final String EXCLUSION_FILTER_OPTION_NAME = "exclusion_filter";
  @NonNls private static final String INSTANCE_ID_OPTION_NAME = "instance_id";
  @NonNls private static final String CONDITION_OPTION_NAME = "CONDITION";
  protected final Project myProject;

  public FilteredRequestorImpl(@NotNull Project project) {
    myProject = project;
    myCondition = new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "");
  }

  public InstanceFilter[] getInstanceFilters() {
    return myInstanceFilters;
  }

  public void setInstanceFilters(InstanceFilter[] instanceFilters) {
    myInstanceFilters = instanceFilters != null? instanceFilters : InstanceFilter.EMPTY_ARRAY;
  }

  public String getSuspendPolicy() {
    return SUSPEND? SUSPEND_POLICY : DebuggerSettings.SUSPEND_NONE;
  }

  /**
   * @return true if the ID was added or false otherwise
   */
  private boolean hasObjectID(long id) {
    for (InstanceFilter instanceFilter : myInstanceFilters) {
      if (instanceFilter.getId() == id) {
        return true;
      }
    }
    return false;
  }

  protected void addInstanceFilter(long l) {
    final InstanceFilter[] filters = new InstanceFilter[myInstanceFilters.length + 1];
    System.arraycopy(myInstanceFilters, 0, filters, 0, myInstanceFilters.length);
    filters[myInstanceFilters.length] = InstanceFilter.create(String.valueOf(l));
    myInstanceFilters = filters;
  }

  public final ClassFilter[] getClassFilters() {
    return myClassFilters;
  }

  public final void setClassFilters(ClassFilter[] classFilters) {
    myClassFilters = classFilters != null? classFilters : ClassFilter.EMPTY_ARRAY;
  }

  public ClassFilter[] getClassExclusionFilters() {
    return myClassExclusionFilters;
  }

  public void setClassExclusionFilters(ClassFilter[] classExclusionFilters) {
    myClassExclusionFilters = classExclusionFilters != null? classExclusionFilters : ClassFilter.EMPTY_ARRAY;
  }

  public void readTo(Element parentNode, Breakpoint breakpoint) throws InvalidDataException {
    readExternal(parentNode);
    if (SUSPEND) {
      breakpoint.setSuspendPolicy(SUSPEND_POLICY);
    }
    else {
      breakpoint.setSuspendPolicy(DebuggerSettings.SUSPEND_NONE);
    }

    breakpoint.setCountFilterEnabled(COUNT_FILTER_ENABLED);
    breakpoint.setCountFilter(COUNT_FILTER);

    breakpoint.setCondition(CONDITION_ENABLED ? myCondition : null);

    breakpoint.setClassFiltersEnabled(CLASS_FILTERS_ENABLED);
    breakpoint.setClassFilters(getClassFilters());
    breakpoint.setClassExclusionFilters(getClassExclusionFilters());

    breakpoint.setInstanceFiltersEnabled(INSTANCE_FILTERS_ENABLED);
    breakpoint.setInstanceFilters(getInstanceFilters());
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);
    if (DebuggerSettings.SUSPEND_NONE.equals(SUSPEND_POLICY)) { // compatibility with older format
      SUSPEND = false;
      SUSPEND_POLICY = DebuggerSettings.SUSPEND_ALL;
    }
    String condition = JDOMExternalizerUtil.readField(parentNode, CONDITION_OPTION_NAME);
    if (condition != null) {
      setCondition(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, condition));
    }

    myClassFilters = DebuggerUtilsEx.readFilters(parentNode.getChildren(FILTER_OPTION_NAME));
    myClassExclusionFilters = DebuggerUtilsEx.readFilters(parentNode.getChildren(EXCLUSION_FILTER_OPTION_NAME));

    final ClassFilter [] instanceFilters = DebuggerUtilsEx.readFilters(parentNode.getChildren(INSTANCE_ID_OPTION_NAME));
    final List<InstanceFilter> iFilters = new ArrayList<InstanceFilter>(instanceFilters.length);

    for (ClassFilter instanceFilter : instanceFilters) {
      try {
        iFilters.add(InstanceFilter.create(instanceFilter));
      }
      catch (Exception e) {
      }
    }
    myInstanceFilters = iFilters.isEmpty() ? InstanceFilter.EMPTY_ARRAY : iFilters.toArray(new InstanceFilter[iFilters.size()]);
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
    JDOMExternalizerUtil.writeField(parentNode, CONDITION_OPTION_NAME, getCondition().toExternalForm());
    DebuggerUtilsEx.writeFilters(parentNode, FILTER_OPTION_NAME, myClassFilters);
    DebuggerUtilsEx.writeFilters(parentNode, EXCLUSION_FILTER_OPTION_NAME, myClassExclusionFilters);
    DebuggerUtilsEx.writeFilters(parentNode, INSTANCE_ID_OPTION_NAME, InstanceFilter.createClassFilters(myInstanceFilters));
  }

  protected String calculateEventClass(EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
    return event.location().declaringType().name();
  }

  private boolean typeMatchesClassFilters(@Nullable String typeName) {
    if (typeName == null) {
      return true;
    }
    boolean matches = false, hasEnabled = false;
    for (ClassFilter classFilter : getClassFilters()) {
      if (classFilter.isEnabled()) {
        hasEnabled = true;
        if (classFilter.matches(typeName)) {
          matches = true;
          break;
        }
      }
    }
    if(hasEnabled && !matches) {
      return false;
    }
    for (ClassFilter classFilter : getClassExclusionFilters()) {
      if (classFilter.isEnabled() && classFilter.matches(typeName)) {
        return false;
      }
    }
    return true;
  }

  public PsiElement getEvaluationElement() {
    return null;
  }

  public TextWithImports getCondition() {
    return myCondition;
  }

  public void setCondition(TextWithImports condition) {
    myCondition = condition;
  }

  public Project getProject() {
    return myProject;
  }

  public boolean isCountFilterEnabled() {
    return COUNT_FILTER_ENABLED;
  }

  public int getCountFilter() {
    return COUNT_FILTER;
  }

  public boolean isClassFiltersEnabled() {
    return CLASS_FILTERS_ENABLED;
  }

  public boolean isInstanceFiltersEnabled() {
    return INSTANCE_FILTERS_ENABLED;
  }

  @Override
  public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event)
    throws EventProcessingException {
    return false;
  }
}
