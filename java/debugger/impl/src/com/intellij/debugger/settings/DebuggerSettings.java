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
package com.intellij.debugger.settings;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.execution.ui.layout.impl.RunnerLayoutSettings;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.classFilter.ClassFilter;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DebuggerSettings implements JDOMExternalizable, ApplicationComponent, Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.settings.DebuggerSettings");
  public static final int SOCKET_TRANSPORT = 0;
  public static final int SHMEM_TRANSPORT = 1;

  public static final @NonNls String SUSPEND_ALL = "SuspendAll";
  public static final @NonNls String SUSPEND_THREAD = "SuspendThread";
  public static final @NonNls String SUSPEND_NONE = "SuspendNone";

  public static final @NonNls String EVALUATE_FRAGMENT = "EvaluateFragment";
  public static final @NonNls String EVALUATE_EXPRESSION = "EvaluateExpression";

  public static final @NonNls String RUN_HOTSWAP_ALWAYS = "RunHotswapAlways";
  public static final @NonNls String RUN_HOTSWAP_NEVER = "RunHotswapNever";
  public static final @NonNls String RUN_HOTSWAP_ASK = "RunHotswapAsk";

  public boolean TRACING_FILTERS_ENABLED;
  public int VALUE_LOOKUP_DELAY; // ms
  public int DEBUGGER_TRANSPORT;
  public boolean FORCE_CLASSIC_VM;
  public boolean DISABLE_JIT;
  public boolean HIDE_DEBUGGER_ON_PROCESS_TERMINATION;
  public boolean HOTSWAP_IN_BACKGROUND = true;
  public boolean SKIP_SYNTHETIC_METHODS;
  public boolean SKIP_CONSTRUCTORS;
  public boolean SKIP_GETTERS;
  public boolean SKIP_CLASSLOADERS;

  public String EVALUATION_DIALOG_TYPE;
  public String RUN_HOTSWAP_AFTER_COMPILE;
  public boolean COMPILE_BEFORE_HOTSWAP;

  public volatile boolean WATCH_RETURN_VALUES = false;
  public volatile boolean AUTO_VARIABLES_MODE = false;
  public volatile boolean SHOW_LIBRARY_STACKFRAMES = true;


  private ClassFilter[] mySteppingFilters = ClassFilter.EMPTY_ARRAY;

  private Map<String, ContentState> myContentStates = new HashMap<String, ContentState>();

  public void disposeComponent() {
  }

  public void initComponent() {}

  public ClassFilter[] getSteppingFilters() {
    final ClassFilter[] rv = new ClassFilter[mySteppingFilters.length];
    for (int idx = 0; idx < rv.length; idx++) {
      rv[idx] = mySteppingFilters[idx].clone();
    }
    return rv;
  }

  void setSteppingFilters(ClassFilter[] steppingFilters) {
    mySteppingFilters = (steppingFilters != null)? steppingFilters : ClassFilter.EMPTY_ARRAY;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void readExternal(Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);
    List<ClassFilter> filtersList = new ArrayList<ClassFilter>();

    for (final Object o : parentNode.getChildren("filter")) {
      Element filter = (Element)o;
      filtersList.add(DebuggerUtilsEx.create(filter));
    }
    setSteppingFilters(filtersList.toArray(new ClassFilter[filtersList.size()]));

    filtersList.clear();

    final List contents = parentNode.getChildren("content");
    myContentStates.clear();
    for (Object content : contents) {
      final ContentState state = new ContentState((Element)content);
      myContentStates.put(state.getType(), state);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
    Element element;
    for (ClassFilter mySteppingFilter : mySteppingFilters) {
      element = new Element("filter");
      parentNode.addContent(element);
      mySteppingFilter.writeExternal(element);
    }

    for (ContentState eachState : myContentStates.values()) {
      final Element content = new Element("content");
      if (eachState.write(content)) {
        parentNode.addContent(content);
      }
    }
  }

  public static DebuggerSettings getInstance() {
    return ServiceManager.getService(DebuggerSettings.class);
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof DebuggerSettings)) return false;
    DebuggerSettings secondSettings = (DebuggerSettings)obj;

    return
      TRACING_FILTERS_ENABLED == secondSettings.TRACING_FILTERS_ENABLED &&
      VALUE_LOOKUP_DELAY == secondSettings.VALUE_LOOKUP_DELAY &&
      DEBUGGER_TRANSPORT == secondSettings.DEBUGGER_TRANSPORT &&
      FORCE_CLASSIC_VM == secondSettings.FORCE_CLASSIC_VM &&
      DISABLE_JIT == secondSettings.DISABLE_JIT &&
      HIDE_DEBUGGER_ON_PROCESS_TERMINATION == secondSettings.HIDE_DEBUGGER_ON_PROCESS_TERMINATION &&
      HOTSWAP_IN_BACKGROUND == secondSettings.HOTSWAP_IN_BACKGROUND &&
      SKIP_SYNTHETIC_METHODS == secondSettings.SKIP_SYNTHETIC_METHODS &&
      SKIP_CLASSLOADERS == secondSettings.SKIP_CLASSLOADERS &&
      SKIP_CONSTRUCTORS == secondSettings.SKIP_CONSTRUCTORS &&
      SKIP_GETTERS == secondSettings.SKIP_GETTERS &&
      COMPILE_BEFORE_HOTSWAP == secondSettings.COMPILE_BEFORE_HOTSWAP &&
      (RUN_HOTSWAP_AFTER_COMPILE != null ? RUN_HOTSWAP_AFTER_COMPILE.equals(secondSettings.RUN_HOTSWAP_AFTER_COMPILE) : secondSettings.RUN_HOTSWAP_AFTER_COMPILE == null) &&
      DebuggerUtilsEx.filterEquals(mySteppingFilters, secondSettings.mySteppingFilters);
  }

  public DebuggerSettings clone() {
    try {
      final DebuggerSettings cloned = (DebuggerSettings)super.clone();
      cloned.myContentStates = new HashMap<String, ContentState>();
      for (Map.Entry<String, ContentState> entry : myContentStates.entrySet()) {
        cloned.myContentStates.put(entry.getKey(), entry.getValue().clone());
      }
      cloned.mySteppingFilters = new ClassFilter[mySteppingFilters.length];
      for (int idx = 0; idx < mySteppingFilters.length; idx++) {
        cloned.mySteppingFilters[idx] = mySteppingFilters[idx].clone();
      }
      return cloned;
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    return null;
  }

  public String getComponentName() {
    return "DebuggerSettings";
  }

  public ContentState getContentState(String type) {
    ContentState state = myContentStates.get(type);
    if (state == null) {
      state = new ContentState(type);
      myContentStates.put(type, state);
    }

    return state;
  }


  public static class ContentState implements Cloneable {

    private final String myType;
    private boolean myMinimized;
    private String mySelectedTab;
    private double mySplitProportion;
    private boolean myDetached;
    private boolean myHorizontalToolbar;
    private boolean myMaximized;

    public ContentState(final String type) {
      myType = type;
    }

    public ContentState(Element element) {
      myType = element.getAttributeValue("type");
      myMinimized = "true".equalsIgnoreCase(element.getAttributeValue("minimized"));
      myMaximized = "true".equalsIgnoreCase(element.getAttributeValue("maximized"));
      mySelectedTab = element.getAttributeValue("selected");
      final String split = element.getAttributeValue("split");
      if (split != null) {
        mySplitProportion = Double.valueOf(split);
      }
      myDetached = "true".equalsIgnoreCase(element.getAttributeValue("detached"));
      myHorizontalToolbar = !"false".equalsIgnoreCase(element.getAttributeValue("horizontal"));
    }

    public boolean write(final Element element) {
      element.setAttribute("type", myType);
      element.setAttribute("minimized", Boolean.valueOf(myMinimized).toString());
      element.setAttribute("maximized", Boolean.valueOf(myMaximized).toString());
      if (mySelectedTab != null) {
        element.setAttribute("selected", mySelectedTab);
      }
      element.setAttribute("split", new Double(mySplitProportion).toString());
      element.setAttribute("detached", Boolean.valueOf(myDetached).toString());
      element.setAttribute("horizontal", Boolean.valueOf(myHorizontalToolbar).toString());
      return true;
    }

    public String getType() {
      return myType;
    }

    public String getSelectedTab() {
      return mySelectedTab;
    }

    public boolean isMinimized() {
      return myMinimized;
    }

    public void setMinimized(final boolean minimized) {
      myMinimized = minimized;
    }

    public void setMaximized(final boolean maximized) {
      myMaximized = maximized;
    }

    public boolean isMaximized() {
      return myMaximized;
    }

    public void setSelectedTab(final String selectedTab) {
      mySelectedTab = selectedTab;
    }

    public void setSplitProportion(double splitProportion) {
      mySplitProportion = splitProportion;
    }

    public double getSplitProportion(double defaultValue) {
      return (mySplitProportion <= 0 || mySplitProportion >= 1) ? defaultValue : mySplitProportion;
    }

    public void setDetached(final boolean detached) {
      myDetached = detached;
    }

    public boolean isDetached() {
      return myDetached;
    }

    public boolean isHorizontalToolbar() {
      return myHorizontalToolbar;
    }

    public void setHorizontalToolbar(final boolean horizontalToolbar) {
      myHorizontalToolbar = horizontalToolbar;
    }

    public ContentState clone() throws CloneNotSupportedException {
      return (ContentState)super.clone();
    }
  }

  public RunnerLayoutSettings getLayoutSettings() {
    return RunnerLayoutSettings.getInstance();
  }


}
