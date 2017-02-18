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

package com.intellij.diagnostic.logging;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
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
@State(name = "LogFilters", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
public class LogConsolePreferences extends LogFilterRegistrar {
  private final SortedMap<LogFilter, Boolean> myRegisteredLogFilters = new TreeMap<>((o1, o2) -> -1);
  @NonNls private static final String FILTER = "filter";
  @NonNls private static final String IS_ACTIVE = "is_active";

  public boolean FILTER_ERRORS = false;
  public boolean FILTER_WARNINGS = false;
  public boolean FILTER_INFO = true;
  public boolean FILTER_DEBUG = true;

  public String CUSTOM_FILTER = null;
  @NonNls public static final String ERROR = "ERROR";
  @NonNls public static final String WARNING = "WARNING";
  @NonNls private static final String WARN = "WARN";
  @NonNls public static final String INFO = "INFO";
  @NonNls public static final String DEBUG = "DEBUG";
  @NonNls public static final String CUSTOM = "CUSTOM";

  public final static Pattern ERROR_PATTERN = Pattern.compile(".*(" + ERROR + "|FATAL).*");
  public final static Pattern WARNING_PATTERN = Pattern.compile(".*" + WARNING + ".*");
  public final static Pattern WARN_PATTERN = Pattern.compile(".*" + WARN + ".*");
  public final static Pattern INFO_PATTERN = Pattern.compile(".*" + INFO + ".*");
  public static final Pattern DEBUG_PATTERN = Pattern.compile(".*" + DEBUG + ".*");

  @NonNls public final static Pattern EXCEPTION_PATTERN = Pattern.compile(".*at .*");

  private final List<LogFilterListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private static final Logger LOG = Logger.getInstance("#" + LogConsolePreferences.class.getName());

  public static LogConsolePreferences getInstance(Project project) {
    return ServiceManager.getService(project, LogConsolePreferences.class);
  }

  public void updateCustomFilter(String customFilter) {
    CUSTOM_FILTER = customFilter;
    fireStateChanged();
  }

  public boolean isApplicable(@NotNull String text, String prevType, boolean checkStandardFilters) {
    for (LogFilter filter : myRegisteredLogFilters.keySet()) {
      if (myRegisteredLogFilters.get(filter).booleanValue() && !filter.isAcceptable(text)) return false;
    }
    if (checkStandardFilters) {
      final String type = getType(text);
      boolean selfTyped = false;
      if (type != null) {
        if (!isApplicable(type)) return false;
        selfTyped = true;
      }
      return selfTyped || prevType == null || isApplicable(prevType);
    }
    return true;
  }

  private boolean isApplicable(final String type) {
    if (type.equals(ERROR)) {
      return !FILTER_ERRORS;
    }
    if (type.equals(WARNING)) {
      return !FILTER_WARNINGS;
    }
    if (type.equals(INFO)) {
      return !FILTER_INFO;
    }
    if (type.equals(DEBUG)) {
      return !FILTER_DEBUG;
    }
    return true;
  }

  public static ConsoleViewContentType getContentType(String type) {
    if (type.equals(ERROR)) return ConsoleViewContentType.ERROR_OUTPUT;
    return ConsoleViewContentType.NORMAL_OUTPUT;
  }

  @Nullable
  public static String getType(@NotNull String text) {
    String upcased = StringUtil.toUpperCase(text);
    if (ERROR_PATTERN.matcher(upcased).matches()) return ERROR;
    if (WARNING_PATTERN.matcher(upcased).matches() || WARN_PATTERN.matcher(upcased).matches()) return WARNING;
    if (INFO_PATTERN.matcher(upcased).matches()) return INFO;
    if (DEBUG_PATTERN.matcher(upcased).matches()) return DEBUG;
    return null;
  }

  public static Key getProcessOutputTypes(String type) {
    if (type.equals(ERROR)) return ProcessOutputTypes.STDERR;
    if (type.equals(WARNING) || type.equals(INFO) || type.equals(DEBUG)) return ProcessOutputTypes.STDOUT;
    return null;
  }

  @Override
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

  @Override
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

  @Override
  public void registerFilter(LogFilter filter) {
    myRegisteredLogFilters.put(filter, Boolean.FALSE);
  }

  @Override
  public List<LogFilter> getRegisteredLogFilters() {
    return new ArrayList<>(myRegisteredLogFilters.keySet());
  }

  @Override
  public boolean isFilterSelected(LogFilter filter) {
    final Boolean isSelected = myRegisteredLogFilters.get(filter);
    if (isSelected != null) {
      return isSelected.booleanValue();
    }
    if (filter instanceof IndependentLogFilter) {
      return ((IndependentLogFilter)filter).isSelected();
    }
    return false;
  }

  @Override
  public void setFilterSelected(LogFilter filter, boolean state) {
    if (filter instanceof IndependentLogFilter) {
      ((IndependentLogFilter)filter).selectFilter();
    }
    else if (myRegisteredLogFilters.containsKey(filter)) {
      myRegisteredLogFilters.put(filter, state);
    }
    fireStateChanged(filter);
  }

  public void selectOnlyFilter(LogFilter filter) {
    for (LogFilter logFilter : myRegisteredLogFilters.keySet()) {
      myRegisteredLogFilters.put(logFilter, false);
    }
    if (filter != null) {
      setFilterSelected(filter, true);
    }
  }

  private void fireStateChanged(final LogFilter filter) {
    for (LogFilterListener listener : myListeners) {
      listener.onFilterStateChange(filter);
    }
  }

  private void fireStateChanged() {
    for (LogFilterListener listener : myListeners) {
      listener.onTextFilterChange();
    }
  }

  public void addFilterListener(LogFilterListener l) {
    myListeners.add(l);
  }

  public void removeFilterListener(LogFilterListener l) {
    myListeners.remove(l);
  }

}
