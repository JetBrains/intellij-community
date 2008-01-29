/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.diagnostic.logging;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/**
 * User: anna
 * Date: 06-Feb-2006
 */
@State(
  name="LogFilters",
  storages = {
    @Storage(
      id="LogFilters",
      file="$WORKSPACE_FILE$"
    )}
)
@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
public class LogConsolePreferences extends LogFilterRegistrar {
  private SortedMap<LogFilter, Boolean> myRegisteredLogFilters = new TreeMap<LogFilter, Boolean>(new Comparator<LogFilter>() {
    public int compare(final LogFilter o1, final LogFilter o2) {
      return -1;
    }
  });
  @NonNls private static final String FILTER = "filter";
  @NonNls private static final String IS_ACTIVE = "is_active";

  public boolean FILTER_ERRORS = false;
  public boolean FILTER_WARNINGS = false;
  public boolean FILTER_INFO = true;
  public String CUSTOM_FILTER = null;
  @NonNls public static final String ERROR = "ERROR";
  @NonNls public static final String WARNING = "WARNING";
  @NonNls private static final String WARN = "WARN";
  @NonNls public static final String INFO = "INFO";
  @NonNls public static final String CUSTOM = "CUSTOM";

  public final static Pattern ERROR_PATTERN = Pattern.compile(".*" + ERROR + ".*");
  public final static Pattern WARNING_PATTERN = Pattern.compile(".*" + WARNING + ".*");
  public final static Pattern WARN_PATTERN = Pattern.compile(".*" + WARN + ".*");
  public final static Pattern INFO_PATTERN = Pattern.compile(".*" + INFO + ".*");

  private static Pattern ourCustomPattern = null;

  @NonNls public final static Pattern EXCEPTION_PATTERN = Pattern.compile(".*at .*");

  private List<FilterListener> myListeners = new ArrayList<FilterListener>();
  private static final Logger LOG = Logger.getInstance("#" + LogConsolePreferences.class.getName());

  public static LogConsolePreferences getInstance(Project project){
    return ServiceManager.getService(project, LogConsolePreferences.class);
  }

  public void updateCustomFilter(String customFilter) {
    CUSTOM_FILTER = customFilter;
    ourCustomPattern = null;
    fireStateChanged();
  }

  @Nullable
  private Pattern getCustomPattern() {
    if (ourCustomPattern == null && CUSTOM_FILTER != null) {
      final StringBuilder buf = StringBuilderSpinAllocator.alloc();
      try {
        for (int i = 0; i < CUSTOM_FILTER.length(); i++) {
          final char c = CUSTOM_FILTER.charAt(i);
          if (Character.isLetterOrDigit(c)) {
            buf.append(Character.toUpperCase(c));
          } else {
            buf.append("\\").append(c);
          }
        }
        ourCustomPattern = Pattern.compile(".*" + buf + ".*");
      }
      finally {
        StringBuilderSpinAllocator.dispose(buf);
      }
    }
    return ourCustomPattern;
  }

  public boolean isApplicable(@NotNull String text, String prevType){
    if (CUSTOM_FILTER != null) {
      final Pattern pattern = getCustomPattern();
      if (pattern != null && !pattern.matcher(text.toUpperCase()).matches()) return false;
    }
    final String type = getType(text);
    boolean selfTyped = false;
    if (type != null) {
      if (!isApplicable(type)) return false;
      selfTyped = true;
    }
    for (LogFilter filter : myRegisteredLogFilters.keySet()) {
      if (myRegisteredLogFilters.get(filter).booleanValue() && !filter.isAcceptable(text)) return false;
    }
    return selfTyped || prevType == null || isApplicable(prevType);
  }

  private boolean isApplicable(final String type) {
    if (type.equals(ERROR)){
      return !FILTER_ERRORS;
    }
    if (type.equals(WARNING)){
      return !FILTER_WARNINGS;
    }
    if (type.equals(INFO)){
      return !FILTER_INFO;
    }
    return true;
  }

  public static ConsoleViewContentType getContentType(String type){
    if (type.equals(ERROR)) return ConsoleViewContentType.ERROR_OUTPUT;
    return ConsoleViewContentType.NORMAL_OUTPUT;
  }

  @Nullable
  public static String getType(@NotNull String text){
    if (ERROR_PATTERN.matcher(text.toUpperCase()).matches()) return ERROR;
    if (WARNING_PATTERN.matcher(text.toUpperCase()).matches() || WARN_PATTERN.matcher(text.toUpperCase()).matches()) return WARNING;
    if (INFO_PATTERN.matcher(text.toUpperCase()).matches()) return INFO;
    return null;
  }

  public static Key getProcessOutputTypes(String type){
    if (type.equals(ERROR)) return ProcessOutputTypes.STDERR;
    if (type.equals(WARNING) || type.equals(INFO)) return ProcessOutputTypes.STDOUT;
    return null;
  }

  public Element getState() {
    @NonNls Element element = new Element("LogFilters");
    try {
      for (LogFilter filter : myRegisteredLogFilters.keySet()) {
        Element filterElement = new Element(FILTER);
        filterElement.setAttribute(IS_ACTIVE, myRegisteredLogFilters.get(filter).toString());
        filter.writeExternal(filterElement);
        element.addContent(filterElement);
      }
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return element;
  }

  public void loadState(final Element object) {
    try {
      final List children = object.getChildren(FILTER);
      for (Object child : children) {
        Element filterElement = (Element)child;
        final LogFilter filter = new LogFilter();
        filter.readExternal(filterElement);
        setFilterSelected(filter, Boolean.parseBoolean(filterElement.getAttributeValue(IS_ACTIVE)));
      }
      DefaultJDOMExternalizer.readExternal(this, object);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public void registerFilter(LogFilter filter){
    myRegisteredLogFilters.put(filter, Boolean.FALSE);
  }

  public List<LogFilter> getRegisteredLogFilters() {
    return new ArrayList<LogFilter>(myRegisteredLogFilters.keySet());
  }

  public boolean isFilterSelected(LogFilter filter){
    final Boolean isSelected = myRegisteredLogFilters.get(filter);
    if (isSelected != null) {
      return isSelected.booleanValue();
    }
    if (filter.getName().indexOf(ERROR) != -1) return FILTER_ERRORS;
    if (filter.getName().indexOf(WARN) != -1) return FILTER_WARNINGS;
    return filter.getName().indexOf(INFO) == -1 || FILTER_INFO;
  }

  public void setFilterSelected(LogFilter filter, boolean state){
    if (myRegisteredLogFilters.containsKey(filter)){
      myRegisteredLogFilters.put(filter, state);
    } else {
      String filterName = filter.getName();
      if (filterName.indexOf(ERROR) != -1){
        FILTER_ERRORS = state;
      }
      if (filterName.indexOf(WARN) != -1){
        FILTER_WARNINGS = state;
      }
      if (filterName.indexOf(INFO) != -1){
        FILTER_INFO = state;
      }
    }
    fireStateChanged(filter);
  }

  private void fireStateChanged(final LogFilter filter) {
    for (FilterListener listener : myListeners) {
      listener.onFilterStateChange(filter);
    }
  }

  private void fireStateChanged() {
    for (FilterListener listener : myListeners) {
      listener.onTextFilterChange();
    }
  }

  public void addFilterListener(FilterListener l) {
    myListeners.add(l);
  }

  public void removeFilterListener(FilterListener l) {
    myListeners.remove(l);
  }

  public interface FilterListener {
    void onFilterStateChange(LogFilter filter);
    void onTextFilterChange();
  }
}
