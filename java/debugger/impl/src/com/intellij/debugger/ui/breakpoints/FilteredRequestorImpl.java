// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.settings.DebuggerSettingsUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.xdebugger.impl.XDebuggerHistoryManager;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.ui.XLightBreakpointPropertiesPanel;
import com.sun.jdi.event.LocatableEvent;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/*
 * Not used any more, since move to xBreakpoints
 */
public class FilteredRequestorImpl implements JDOMExternalizable, FilteredRequestor {

  public String SUSPEND_POLICY = DebuggerSettings.SUSPEND_ALL;
  public boolean SUSPEND = true;

  public boolean COUNT_FILTER_ENABLED = false;
  public int COUNT_FILTER = 0;

  public boolean CONDITION_ENABLED = false;
  private TextWithImports myCondition;

  public boolean CLASS_FILTERS_ENABLED = false;
  private ClassFilter[] myClassFilters = ClassFilter.EMPTY_ARRAY;
  private ClassFilter[] myClassExclusionFilters = ClassFilter.EMPTY_ARRAY;

  public boolean INSTANCE_FILTERS_ENABLED = false;
  private InstanceFilter[] myInstanceFilters = InstanceFilter.EMPTY_ARRAY;

  private static final @NonNls String FILTER_OPTION_NAME = "filter";
  private static final @NonNls String EXCLUSION_FILTER_OPTION_NAME = "exclusion_filter";
  private static final @NonNls String INSTANCE_ID_OPTION_NAME = "instance_id";
  private static final @NonNls String CONDITION_OPTION_NAME = "CONDITION";
  protected final Project myProject;

  public FilteredRequestorImpl(@NotNull Project project) {
    myProject = project;
    myCondition = new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "");
  }

  @Override
  public InstanceFilter[] getInstanceFilters() {
    return myInstanceFilters;
  }

  public void setInstanceFilters(InstanceFilter[] instanceFilters) {
    myInstanceFilters = instanceFilters != null ? instanceFilters : InstanceFilter.EMPTY_ARRAY;
  }

  @Override
  public String getSuspendPolicy() {
    return SUSPEND ? SUSPEND_POLICY : DebuggerSettings.SUSPEND_NONE;
  }

  @Override
  public final ClassFilter[] getClassFilters() {
    return myClassFilters;
  }

  public final void setClassFilters(ClassFilter[] classFilters) {
    myClassFilters = classFilters != null ? classFilters : ClassFilter.EMPTY_ARRAY;
  }

  @Override
  public ClassFilter[] getClassExclusionFilters() {
    return myClassExclusionFilters;
  }

  public void setClassExclusionFilters(ClassFilter[] classExclusionFilters) {
    myClassExclusionFilters = classExclusionFilters != null ? classExclusionFilters : ClassFilter.EMPTY_ARRAY;
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

    breakpoint.setCondition(myCondition);
    ((XBreakpointBase<?, ?, ?>)breakpoint.myXBreakpoint).setConditionEnabled(CONDITION_ENABLED);
    if (myCondition != null && !myCondition.isEmpty()) {
      XDebuggerHistoryManager.getInstance(myProject).addRecentExpression(XLightBreakpointPropertiesPanel.CONDITION_HISTORY_ID, TextWithImportsImpl.toXExpression(myCondition));
    }

    breakpoint.setClassFiltersEnabled(CLASS_FILTERS_ENABLED);
    breakpoint.setClassFilters(getClassFilters());
    breakpoint.setClassExclusionFilters(getClassExclusionFilters());

    breakpoint.setInstanceFiltersEnabled(INSTANCE_FILTERS_ENABLED);
    breakpoint.setInstanceFilters(getInstanceFilters());
  }

  @Override
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

    myClassFilters = DebuggerSettingsUtils.readFilters(parentNode.getChildren(FILTER_OPTION_NAME));
    myClassExclusionFilters = DebuggerSettingsUtils.readFilters(parentNode.getChildren(EXCLUSION_FILTER_OPTION_NAME));

    final ClassFilter[] instanceFilters = DebuggerSettingsUtils.readFilters(parentNode.getChildren(INSTANCE_ID_OPTION_NAME));
    final List<InstanceFilter> iFilters = new ArrayList<>(instanceFilters.length);

    for (ClassFilter instanceFilter : instanceFilters) {
      try {
        iFilters.add(InstanceFilter.create(instanceFilter));
      }
      catch (Exception ignored) {
      }
    }
    myInstanceFilters = iFilters.isEmpty() ? InstanceFilter.EMPTY_ARRAY : iFilters.toArray(InstanceFilter.EMPTY_ARRAY);
  }

  @Override
  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
    JDOMExternalizerUtil.writeField(parentNode, CONDITION_OPTION_NAME, getCondition().toExternalForm());
    DebuggerSettingsUtils.writeFilters(parentNode, FILTER_OPTION_NAME, myClassFilters);
    DebuggerSettingsUtils.writeFilters(parentNode, EXCLUSION_FILTER_OPTION_NAME, myClassExclusionFilters);
    DebuggerSettingsUtils.writeFilters(parentNode, INSTANCE_ID_OPTION_NAME, InstanceFilter.createClassFilters(myInstanceFilters));
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

  @Override
  public boolean isCountFilterEnabled() {
    return COUNT_FILTER_ENABLED;
  }

  @Override
  public int getCountFilter() {
    return COUNT_FILTER;
  }

  @Override
  public boolean isClassFiltersEnabled() {
    return CLASS_FILTERS_ENABLED;
  }

  @Override
  public boolean isInstanceFiltersEnabled() {
    return INSTANCE_FILTERS_ENABLED;
  }

  @Override
  public boolean processLocatableEvent(@NotNull SuspendContextCommandImpl action, LocatableEvent event)
    throws EventProcessingException {
    return false;
  }
}
