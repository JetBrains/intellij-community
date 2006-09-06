package com.intellij.debugger.settings;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.colors.ColorSettingsPages;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.classFilter.ClassFilter;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

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
  public boolean HIDE_DEBUGGER_ON_PROCESS_TERMINATION;
  public boolean SKIP_SYNTHETIC_METHODS;
  public boolean SKIP_CONSTRUCTORS;
  public boolean SKIP_GETTERS;
  public boolean SKIP_CLASSLOADERS;

  public String EVALUATION_DIALOG_TYPE;
  public String STEP_THREAD_SUSPEND_POLICY;
  public String RUN_HOTSWAP_AFTER_COMPILE;

  private ClassFilter[] mySteppingFilters = ClassFilter.EMPTY_ARRAY;

  public DebuggerSettings(ColorSettingsPages pages) {
    if (pages != null) {
      pages.registerPage(new DebuggerColorsPage());
    }
  }

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
      HIDE_DEBUGGER_ON_PROCESS_TERMINATION == secondSettings.HIDE_DEBUGGER_ON_PROCESS_TERMINATION &&
      SKIP_SYNTHETIC_METHODS == secondSettings.SKIP_SYNTHETIC_METHODS &&
      SKIP_CLASSLOADERS == secondSettings.SKIP_CLASSLOADERS &&
      SKIP_CONSTRUCTORS == secondSettings.SKIP_CONSTRUCTORS &&
      SKIP_GETTERS == secondSettings.SKIP_GETTERS &&
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
}