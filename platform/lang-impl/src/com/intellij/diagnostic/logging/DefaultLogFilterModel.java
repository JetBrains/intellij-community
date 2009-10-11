package com.intellij.diagnostic.logging;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class DefaultLogFilterModel extends LogFilterModel {
  private final Project myProject;
  private boolean myCheckStandartFilters = true;
  private String myPrevType = null;

  public DefaultLogFilterModel(Project project) {
    myProject = project;
  }

  protected LogConsolePreferences getPreferences() {
    return LogConsolePreferences.getInstance(myProject);
  }

  public boolean isCheckStandartFilters() {
    return myCheckStandartFilters;
  }

  public void setCheckStandartFilters(boolean checkStandartFilters) {
    myCheckStandartFilters = checkStandartFilters;
  }

  public void updateCustomFilter(String filter) {
    super.updateCustomFilter(filter);
    getPreferences().updateCustomFilter(filter);
  }

  public String getCustomFilter() {
    return getPreferences().CUSTOM_FILTER;
  }

  public void addFilterListener(LogFilterListener listener) {
    getPreferences().addFilterListener(listener);
  }

  public boolean isApplicable(String line) {
    if (!super.isApplicable(line)) return false;
    return getPreferences().isApplicable(line, myPrevType, myCheckStandartFilters);
  }

  public void removeFilterListener(LogFilterListener listener) {
    getPreferences().removeFilterListener(listener);
  }

  public List<LogFilter> getLogFilters() {
    LogConsolePreferences prefs = getPreferences();
    final ArrayList<LogFilter> filters = new ArrayList<LogFilter>();
    if (myCheckStandartFilters) {
      addStandartFilters(filters, prefs);
    }
    filters.addAll(prefs.getRegisteredLogFilters());
    return filters;
  }

  private void addStandartFilters(ArrayList<LogFilter> filters, final LogConsolePreferences prefs) {
    filters.add(new MyFilter(DiagnosticBundle.message("log.console.filter.show.all"), prefs) {
      @Override
      public void selectFilter() {
        prefs.FILTER_ERRORS = false;
        prefs.FILTER_INFO = false;
        prefs.FILTER_WARNINGS = false;
        prefs.FILTER_DEBUG = false;
      }

      @Override
      public boolean isSelected() {
        return !prefs.FILTER_ERRORS && !prefs.FILTER_INFO && !prefs.FILTER_WARNINGS && !prefs.FILTER_DEBUG;
      }
    });
    filters.add(new MyFilter(DiagnosticBundle.message("log.console.filter.show.errors.warnings.and.infos"), prefs) {
      @Override
      public void selectFilter() {
        prefs.FILTER_ERRORS = false;
        prefs.FILTER_INFO = false;
        prefs.FILTER_WARNINGS = false;
        prefs.FILTER_DEBUG = true;
      }

      @Override
      public boolean isSelected() {
        return !prefs.FILTER_ERRORS && !prefs.FILTER_INFO && !prefs.FILTER_WARNINGS && prefs.FILTER_DEBUG;
      }
    });
    filters.add(new MyFilter(DiagnosticBundle.message("log.console.filter.show.errors.and.warnings"), prefs) {
      @Override
      public void selectFilter() {
        prefs.FILTER_ERRORS = false;
        prefs.FILTER_INFO = true;
        prefs.FILTER_WARNINGS = false;
        prefs.FILTER_DEBUG = true;
      }

      @Override
      public boolean isSelected() {
        return !prefs.FILTER_ERRORS && prefs.FILTER_INFO && !prefs.FILTER_WARNINGS && prefs.FILTER_DEBUG;
      }
    });
    filters.add(new MyFilter(DiagnosticBundle.message("log.console.filter.show.errors"), prefs) {
      @Override
      public void selectFilter() {
        prefs.FILTER_ERRORS = false;
        prefs.FILTER_INFO = true;
        prefs.FILTER_WARNINGS = true;
        prefs.FILTER_DEBUG = true;
      }

      @Override
      public boolean isSelected() {
        return !prefs.FILTER_ERRORS && prefs.FILTER_INFO && prefs.FILTER_WARNINGS && prefs.FILTER_DEBUG;
      }
    });
  }

  public boolean isFilterSelected(LogFilter filter) {
    return getPreferences().isFilterSelected(filter);
  }

  public void selectFilter(LogFilter filter) {
    getPreferences().selectOnlyFilter(filter);
  }

  public Key processLine(String line) {
    final String type = LogConsolePreferences.getType(line);
    Key contentType = type != null
                      ? LogConsolePreferences.getProcessOutputTypes(type)
                      : (myPrevType == LogConsolePreferences.ERROR ? ProcessOutputTypes.STDERR : ProcessOutputTypes.STDOUT);
    if (type != null) {
      myPrevType = type;
    }
    return contentType;
  }

  private abstract class MyFilter extends IndependentLogFilter {
    private final LogConsolePreferences myPrefs;

    protected MyFilter(String name, LogConsolePreferences prefs) {
      super(name);
      myPrefs = prefs;
    }

    public boolean isAcceptable(String line) {
      return myPrefs.isApplicable(line, myPrevType, myCheckStandartFilters);
    }
  }
}
