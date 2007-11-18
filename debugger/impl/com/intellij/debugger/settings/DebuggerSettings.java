package com.intellij.debugger.settings;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.debugger.ui.content.newUI.NewContentState;
import com.intellij.debugger.ui.content.newUI.PlaceInGrid;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.*;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.content.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DebuggerSettings implements JDOMExternalizable, ApplicationComponent {
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
  public String STEP_THREAD_SUSPEND_POLICY;
  public String RUN_HOTSWAP_AFTER_COMPILE;
  public boolean COMPILE_BEFORE_HOTSWAP;

  public volatile boolean WATCH_RETURN_VALUES = false;
  public volatile boolean AUTO_VARIABLES_MODE = false;

  public volatile boolean HORIZONTAL_TOOLBAR = false;

  private ClassFilter[] mySteppingFilters = ClassFilter.EMPTY_ARRAY;

  private Map<String, ContentState> myContentStates = new HashMap<String, ContentState>();

  public void disposeComponent() {
  }

  public void initComponent() {}

  public ClassFilter[] getSteppingFilters() {
    return retrieveFilters(mySteppingFilters);
  }

  private ClassFilter[] retrieveFilters(ClassFilter[] filters) {
    ClassFilter[] rv = new ClassFilter[filters.length];
    for (int idx = 0; idx < rv.length; idx++) {
      rv[idx] = filters[idx].clone();
    }
    return rv;
  }

  public boolean isNameFiltered(String qName) {
    if (!TRACING_FILTERS_ENABLED) {
      return false;
    }
    return DebuggerUtilsEx.isFiltered(qName, mySteppingFilters);
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
    return ApplicationManager.getApplication().getComponent(DebuggerSettings.class);
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

  public String getComponentName() {
    return "DebuggerSettings";
  }

  public boolean isSuspendAllThreads() {
    return SUSPEND_ALL.equals(STEP_THREAD_SUSPEND_POLICY);
  }

  public void setSuspendPolicy(boolean suspendAll) {
    STEP_THREAD_SUSPEND_POLICY = suspendAll ? SUSPEND_ALL : SUSPEND_THREAD;
  }

  public ContentState getContentState(String type) {
    ContentState state = myContentStates.get(type);
    if (state == null) {
      state = new ContentState(type);
      myContentStates.put(type, state);
    }

    return state;
  }


  public static class ContentState {

    private String myType;
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
  }

  public NewContentState getNewContentState(Content content) {
    final Key kind = content.getUserData(DebuggerContentInfo.CONTENT_KIND);
    if (DebuggerContentInfo.FRAME_CONTENT.equals(kind)) {
      return new NewContentState(0, PlaceInGrid.left, getSplitProportion(PlaceInGrid.left));
    } else if (DebuggerContentInfo.VARIABLES_CONTENT.equals(kind)) {
      return new NewContentState(0, PlaceInGrid.center, getSplitProportion(PlaceInGrid.center));
    } else if (DebuggerContentInfo.WATCHES_CONTENT.equals(kind)) {
      return new NewContentState(0, PlaceInGrid.right, getSplitProportion(PlaceInGrid.right));
    } else if (DebuggerContentInfo.CONSOLE_CONTENT.equals(kind)) {
      return new NewContentState(1, PlaceInGrid.bottom, getSplitProportion(PlaceInGrid.bottom));
    } else {
      return new NewContentState(Integer.MAX_VALUE, PlaceInGrid.unknown, getSplitProportion(PlaceInGrid.unknown));
    }
  }

  public float getSplitProportion(PlaceInGrid placeInGrid) {
    switch (placeInGrid) {
      case left:
        return .2f;
      case center:
        return 0f;
      case right:
        return .2f;
      case bottom:
        return .5f;
      default:
        return -1f;
    }
  }


  public @Nullable
  String getTabTitle(final int index) {
    return index == 0 ? "Debugger" : null;
  }

  @Nullable
  public Icon getTabIcon(final int tabIndex) {
    return null;
  }

  public boolean isToolbarHorizontal() {
    return HORIZONTAL_TOOLBAR;
  }

  public void setToolbarHorizontal(boolean horizontal) {
    HORIZONTAL_TOOLBAR = horizontal;
  }
}