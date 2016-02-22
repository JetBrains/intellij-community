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
package com.intellij.debugger.settings;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@State(
  name = "DebuggerSettings",
  defaultStateAsResource = true,
  storages = {
    @Storage("debugger.xml"),
    @Storage(value = "other.xml", deprecated = true)
  }
)
public class DebuggerSettings implements Cloneable, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(DebuggerSettings.class);
  public static final int SOCKET_TRANSPORT = 0;
  public static final int SHMEM_TRANSPORT = 1;

  @NonNls public static final String SUSPEND_ALL = "SuspendAll";
  @NonNls public static final String SUSPEND_THREAD = "SuspendThread";
  @NonNls public static final String SUSPEND_NONE = "SuspendNone";

  @NonNls public static final String EVALUATE_FRAGMENT = "EvaluateFragment";
  @NonNls public static final String EVALUATE_EXPRESSION = "EvaluateExpression";

  @NonNls public static final String RUN_HOTSWAP_ALWAYS = "RunHotswapAlways";
  @NonNls public static final String RUN_HOTSWAP_NEVER = "RunHotswapNever";
  @NonNls public static final String RUN_HOTSWAP_ASK = "RunHotswapAsk";

  @NonNls public static final String EVALUATE_FINALLY_ALWAYS = "EvaluateFinallyAlways";
  @NonNls public static final String EVALUATE_FINALLY_NEVER = "EvaluateFinallyNever";
  @NonNls public static final String EVALUATE_FINALLY_ASK = "EvaluateFinallyAsk";

  public boolean TRACING_FILTERS_ENABLED;
  public int DEBUGGER_TRANSPORT;
  public boolean FORCE_CLASSIC_VM;
  public boolean DISABLE_JIT;
  public boolean SHOW_ALTERNATIVE_SOURCE = true;
  public boolean HOTSWAP_IN_BACKGROUND = true;
  public boolean SKIP_SYNTHETIC_METHODS;
  public boolean SKIP_CONSTRUCTORS;
  public boolean SKIP_GETTERS;
  public boolean SKIP_CLASSLOADERS;

  public String EVALUATION_DIALOG_TYPE;
  public String RUN_HOTSWAP_AFTER_COMPILE;
  public boolean COMPILE_BEFORE_HOTSWAP;
  public boolean HOTSWAP_HANG_WARNING_ENABLED = false;

  public volatile boolean WATCH_RETURN_VALUES = false;
  public volatile boolean AUTO_VARIABLES_MODE = false;

  public String EVALUATE_FINALLY_ON_POP_FRAME = EVALUATE_FINALLY_ASK;

  public boolean RESUME_ONLY_CURRENT_THREAD = false;

  private ClassFilter[] mySteppingFilters = ClassFilter.EMPTY_ARRAY;

  private Map<String, ContentState> myContentStates = new LinkedHashMap<>();

  // transient - custom serialization
  @Transient
  public ClassFilter[] getSteppingFilters() {
    final ClassFilter[] rv = new ClassFilter[mySteppingFilters.length];
    for (int idx = 0; idx < rv.length; idx++) {
      rv[idx] = mySteppingFilters[idx].clone();
    }
    return rv;
  }

  public static DebuggerSettings getInstance() {
    return ServiceManager.getService(DebuggerSettings.class);
  }

  public void setSteppingFilters(ClassFilter[] steppingFilters) {
    mySteppingFilters = steppingFilters != null ? steppingFilters : ClassFilter.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public Element getState() {
    Element state = XmlSerializer.serialize(this, new SkipDefaultValuesSerializationFilters());
    try {
      DebuggerUtilsEx.writeFilters(state, "filter", mySteppingFilters);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
      return null;
    }

    for (ContentState eachState : myContentStates.values()) {
      final Element content = new Element("content");
      if (eachState.write(content)) {
        state.addContent(content);
      }
    }
    return state;
  }

  @Override
  public void loadState(Element state) {
    XmlSerializer.deserializeInto(this, state);

    try {
      setSteppingFilters(DebuggerUtilsEx.readFilters(state.getChildren("filter")));
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }

    myContentStates.clear();
    for (Element content : state.getChildren("content")) {
      ContentState contentState = new ContentState(content);
      myContentStates.put(contentState.getType(), contentState);
    }
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof DebuggerSettings)) return false;
    DebuggerSettings secondSettings = (DebuggerSettings)obj;

    return
      TRACING_FILTERS_ENABLED == secondSettings.TRACING_FILTERS_ENABLED &&
      DEBUGGER_TRANSPORT == secondSettings.DEBUGGER_TRANSPORT &&
      StringUtil.equals(EVALUATE_FINALLY_ON_POP_FRAME, secondSettings.EVALUATE_FINALLY_ON_POP_FRAME) &&
      FORCE_CLASSIC_VM == secondSettings.FORCE_CLASSIC_VM &&
      DISABLE_JIT == secondSettings.DISABLE_JIT &&
      SHOW_ALTERNATIVE_SOURCE == secondSettings.SHOW_ALTERNATIVE_SOURCE &&
      HOTSWAP_IN_BACKGROUND == secondSettings.HOTSWAP_IN_BACKGROUND &&
      SKIP_SYNTHETIC_METHODS == secondSettings.SKIP_SYNTHETIC_METHODS &&
      SKIP_CLASSLOADERS == secondSettings.SKIP_CLASSLOADERS &&
      SKIP_CONSTRUCTORS == secondSettings.SKIP_CONSTRUCTORS &&
      SKIP_GETTERS == secondSettings.SKIP_GETTERS &&
      RESUME_ONLY_CURRENT_THREAD == secondSettings.RESUME_ONLY_CURRENT_THREAD &&
      COMPILE_BEFORE_HOTSWAP == secondSettings.COMPILE_BEFORE_HOTSWAP &&
      HOTSWAP_HANG_WARNING_ENABLED == secondSettings.HOTSWAP_HANG_WARNING_ENABLED &&
      (RUN_HOTSWAP_AFTER_COMPILE != null ? RUN_HOTSWAP_AFTER_COMPILE.equals(secondSettings.RUN_HOTSWAP_AFTER_COMPILE) : secondSettings.RUN_HOTSWAP_AFTER_COMPILE == null) &&
      DebuggerUtilsEx.filterEquals(mySteppingFilters, secondSettings.mySteppingFilters);
  }

  @Override
  public DebuggerSettings clone() {
    try {
      final DebuggerSettings cloned = (DebuggerSettings)super.clone();
      cloned.myContentStates = new HashMap<>();
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
      element.setAttribute("split", Double.toString(mySplitProportion));
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
      return mySplitProportion <= 0 || mySplitProportion >= 1 ? defaultValue : mySplitProportion;
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

    @Override
    public ContentState clone() throws CloneNotSupportedException {
      return (ContentState)super.clone();
    }
  }
}
