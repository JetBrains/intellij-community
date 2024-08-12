// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.EventDispatcher;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(name = "DebuggerSettings", storages = @Storage("debugger.xml"), category = SettingsCategory.TOOLS)
public final class DebuggerSettings implements Cloneable, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(DebuggerSettings.class);
  public static final int SOCKET_TRANSPORT = 0;
  public static final int SHMEM_TRANSPORT = 1;

  @NonNls public static final String SUSPEND_ALL = "SuspendAll";
  @NonNls public static final String SUSPEND_THREAD = "SuspendThread";
  @NonNls public static final String SUSPEND_NONE = "SuspendNone";

  @NonNls public static final String RUN_HOTSWAP_ALWAYS = "RunHotswapAlways";
  @NonNls public static final String RUN_HOTSWAP_NEVER = "RunHotswapNever";
  @NonNls public static final String RUN_HOTSWAP_ASK = "RunHotswapAsk";

  @NonNls public static final String EVALUATE_FINALLY_ALWAYS = "EvaluateFinallyAlways";
  @NonNls public static final String EVALUATE_FINALLY_NEVER = "EvaluateFinallyNever";
  @NonNls public static final String EVALUATE_FINALLY_ASK = "EvaluateFinallyAsk";

  private static final ClassFilter[] DEFAULT_STEPPING_FILTERS = new ClassFilter[]{
    new ClassFilter("com.sun.*"),
    new ClassFilter("java.*"),
    new ClassFilter("javax.*"),
    new ClassFilter("org.omg.*"),
    new ClassFilter("sun.*"),
    new ClassFilter("jdk.internal.*"),
    new ClassFilter("junit.*"),
    new ClassFilter("org.junit.*"),
    new ClassFilter("com.intellij.rt.*"),
    new ClassFilter("com.yourkit.runtime.*"),
    new ClassFilter("com.springsource.loaded.*"),
    new ClassFilter("org.springsource.loaded.*"),
    new ClassFilter("javassist.*"),
    new ClassFilter("org.apache.webbeans.*"),
    new ClassFilter("com.ibm.ws.*"),
    new ClassFilter("org.mockito.*")
  };

  public boolean TRACING_FILTERS_ENABLED = true;

  @OptionTag("DEBUGGER_TRANSPORT")
  private int DEBUGGER_TRANSPORT;

  public boolean DISABLE_JIT;
  public boolean SHOW_ALTERNATIVE_SOURCE = true;
  public volatile boolean ENABLE_MEMORY_AGENT =
    ApplicationManager.getApplication().isEAP() && !ApplicationManager.getApplication().isUnitTestMode();
  public boolean ALWAYS_SMART_STEP_INTO = true;
  public boolean SKIP_SYNTHETIC_METHODS = true;
  public boolean SKIP_CONSTRUCTORS;
  public boolean SKIP_GETTERS;
  public boolean SKIP_CLASSLOADERS = true;
  public boolean SHOW_TYPES = true;

  public String RUN_HOTSWAP_AFTER_COMPILE = RUN_HOTSWAP_ASK;
  public boolean COMPILE_BEFORE_HOTSWAP = true;
  public boolean HOTSWAP_HANG_WARNING_ENABLED = false;
  public boolean HOTSWAP_SHOW_FLOATING_BUTTON = true;

  public volatile boolean WATCH_RETURN_VALUES = false;
  public volatile boolean AUTO_VARIABLES_MODE = false;

  public volatile boolean KILL_PROCESS_IMMEDIATELY = false;
  public volatile boolean ALWAYS_DEBUG = true;

  public String EVALUATE_FINALLY_ON_POP_FRAME = EVALUATE_FINALLY_ASK;

  /**
   * Whether we resume only current thread during the stepping.
   */
  public boolean RESUME_ONLY_CURRENT_THREAD = false;

  /**
   * Whether we hide not only library frames in stack view, but also the frames from classes which are filtered from the stepping.
   */
  public boolean HIDE_STACK_FRAMES_USING_STEPPING_FILTER = true;

  private ClassFilter[] mySteppingFilters = DEFAULT_STEPPING_FILTERS;

  public boolean INSTRUMENTING_AGENT = true;
  private List<CapturePoint> myCapturePoints = new ArrayList<>();
  public boolean CAPTURE_VARIABLES;
  private final EventDispatcher<CapturePointsSettingsListener> myDispatcher = EventDispatcher.create(CapturePointsSettingsListener.class);

  private Map<String, ContentState> myContentStates = new LinkedHashMap<>();

  // transient - custom serialization
  @Transient
  public ClassFilter[] getSteppingFilters() {
    return ClassFilter.deepCopyOf(mySteppingFilters);
  }

  public static DebuggerSettings getInstance() {
    return ApplicationManager.getApplication().getService(DebuggerSettings.class);
  }

  public void setSteppingFilters(ClassFilter[] steppingFilters) {
    mySteppingFilters = steppingFilters != null ? steppingFilters : ClassFilter.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public Element getState() {
    Element state = XmlSerializer.serialize(this);
    if (state == null) {
      state = new Element("state");
    }

    if (!Arrays.equals(DEFAULT_STEPPING_FILTERS, mySteppingFilters)) {
      DebuggerUtilsEx.writeFilters(state, "filter", mySteppingFilters);
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
  public void loadState(@NotNull Element state) {
    XmlSerializer.deserializeInto(state, this);

    List<Element> steppingFiltersElement = state.getChildren("filter");
    if (steppingFiltersElement.isEmpty()) {
      setSteppingFilters(DEFAULT_STEPPING_FILTERS);
    }
    else {
      setSteppingFilters(DebuggerUtilsEx.readFilters(steppingFiltersElement));
    }

    myContentStates.clear();
    for (Element content : state.getChildren("content")) {
      ContentState contentState = new ContentState(content);
      myContentStates.put(contentState.getType(), contentState);
    }
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof DebuggerSettings secondSettings)) return false;

    return
      TRACING_FILTERS_ENABLED == secondSettings.TRACING_FILTERS_ENABLED &&
      DEBUGGER_TRANSPORT == secondSettings.DEBUGGER_TRANSPORT &&
      StringUtil.equals(EVALUATE_FINALLY_ON_POP_FRAME, secondSettings.EVALUATE_FINALLY_ON_POP_FRAME) &&
      DISABLE_JIT == secondSettings.DISABLE_JIT &&
      SHOW_ALTERNATIVE_SOURCE == secondSettings.SHOW_ALTERNATIVE_SOURCE &&
      KILL_PROCESS_IMMEDIATELY == secondSettings.KILL_PROCESS_IMMEDIATELY &&
      ALWAYS_DEBUG == secondSettings.ALWAYS_DEBUG &&
      ENABLE_MEMORY_AGENT == secondSettings.ENABLE_MEMORY_AGENT &&
      ALWAYS_SMART_STEP_INTO == secondSettings.ALWAYS_SMART_STEP_INTO &&
      SKIP_SYNTHETIC_METHODS == secondSettings.SKIP_SYNTHETIC_METHODS &&
      SKIP_CLASSLOADERS == secondSettings.SKIP_CLASSLOADERS &&
      SKIP_CONSTRUCTORS == secondSettings.SKIP_CONSTRUCTORS &&
      SKIP_GETTERS == secondSettings.SKIP_GETTERS &&
      SHOW_TYPES == secondSettings.SHOW_TYPES &&
      RESUME_ONLY_CURRENT_THREAD == secondSettings.RESUME_ONLY_CURRENT_THREAD &&
      HIDE_STACK_FRAMES_USING_STEPPING_FILTER == secondSettings.HIDE_STACK_FRAMES_USING_STEPPING_FILTER &&
      COMPILE_BEFORE_HOTSWAP == secondSettings.COMPILE_BEFORE_HOTSWAP &&
      HOTSWAP_HANG_WARNING_ENABLED == secondSettings.HOTSWAP_HANG_WARNING_ENABLED &&
      Objects.equals(RUN_HOTSWAP_AFTER_COMPILE, secondSettings.RUN_HOTSWAP_AFTER_COMPILE) &&
      DebuggerUtilsEx.filterEquals(mySteppingFilters, secondSettings.mySteppingFilters) &&
      myCapturePoints.equals(secondSettings.myCapturePoints);
  }

  @Override
  public DebuggerSettings clone() {
    try {
      final DebuggerSettings cloned = (DebuggerSettings)super.clone();
      cloned.myContentStates = new HashMap<>();
      for (Map.Entry<String, ContentState> entry : myContentStates.entrySet()) {
        cloned.myContentStates.put(entry.getKey(), entry.getValue().clone());
      }
      cloned.mySteppingFilters = ClassFilter.deepCopyOf(mySteppingFilters);
      cloned.myCapturePoints = cloneCapturePoints();
      return cloned;
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    return null;
  }

  List<CapturePoint> cloneCapturePoints() {
    try {
      ArrayList<CapturePoint> res = new ArrayList<>(myCapturePoints.size());
      for (CapturePoint point : myCapturePoints) {
        res.add(point.clone());
      }
      return res;
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    return Collections.emptyList();
  }

  @XCollection(propertyElementName = "capture-points")
  public List<CapturePoint> getCapturePoints() {
    return myCapturePoints;
  }

  // for serialization, do not remove
  @SuppressWarnings("unused")
  public void setCapturePoints(List<CapturePoint> capturePoints) {
    myCapturePoints = capturePoints;
    myDispatcher.getMulticaster().capturePointsChanged();
  }

  public void addCapturePointsSettingsListener(CapturePointsSettingsListener listener, Disposable disposable) {
    myDispatcher.addListener(listener, disposable);
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
      myMinimized = Boolean.parseBoolean(element.getAttributeValue("minimized"));
      myMaximized = Boolean.parseBoolean(element.getAttributeValue("maximized"));
      mySelectedTab = element.getAttributeValue("selected");
      final String split = element.getAttributeValue("split");
      if (split != null) {
        mySplitProportion = Double.parseDouble(split);
      }
      myDetached = Boolean.parseBoolean(element.getAttributeValue("detached"));
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

  public interface CapturePointsSettingsListener extends EventListener {
    void capturePointsChanged();
  }

  @Transient
  public int getTransport() {
    if (!SystemInfo.isWindows) {
      return SOCKET_TRANSPORT;
    }
    return DEBUGGER_TRANSPORT;
  }

  @Transient
  public void setTransport(int transport) {
    DEBUGGER_TRANSPORT = transport;
  }
}
