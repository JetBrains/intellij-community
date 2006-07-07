/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.versionBrowser;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class ChangeBrowserSettings implements ProjectComponent, JDOMExternalizable {
  public interface Filter {
    boolean accepts(RepositoryVersion change);
  }

  public float MAIN_SPLITTER_PROPORTION = 0.3f;
  public float MESSAGES_SPLITTER_PROPORTION = 0.8f;

  public boolean USE_DATE_BEFORE_FILTER = false;
  public boolean USE_DATE_AFTER_FILTER = false;
  public boolean USE_CHANGE_BEFORE_FILTER = false;
  public boolean USE_CHANGE_AFTER_FILTER = false;


  public String DATE_BEFORE = "";
  public String DATE_AFTER = "";

  @NonNls public String CHANGE_BEFORE = "";
  public String CHANGE_AFTER = "";

  public boolean USE_USER_FILTER = false;
  public String USER = "";

  private static final DateFormat DATE_FORMAT = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.LONG, SimpleDateFormat.LONG);


  public static ChangeBrowserSettings getSettings(Project project) {
    return project.getComponent(ChangeBrowserSettings.class);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "ChangeBrowserSettings";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  private Date parseDate(final String dateStr) {
    if (dateStr == null) return null;
    try {
      return DATE_FORMAT.parse(dateStr);
    }
    catch (Exception e) {
      return null;
    }
  }

  public void setDateBefore(final Date value) {
    if (value == null) {
      DATE_BEFORE = null;
    }
    else {
      DATE_BEFORE = DATE_FORMAT.format(value);
    }
  }

  public Date getDateBefore() {
    return parseDate(DATE_BEFORE);
  }

  public Date getDateAfter() {
    if (USE_DATE_AFTER_FILTER) {
      return parseDate(DATE_AFTER);
    }
    else {
      return null;
    }
  }

  public Long getChangeBeforeFilter() {
    if (USE_CHANGE_BEFORE_FILTER && CHANGE_BEFORE.length() > 0) {
      if (CHANGE_BEFORE.equals("HEAD")) return null;
      return Long.parseLong(CHANGE_BEFORE);      
    }
    return null;
  }

  public Date getDateBeforeFilter() {
    if (USE_DATE_BEFORE_FILTER) {
      return parseDate(DATE_BEFORE);
    }
    else {
      return null;
    }
  }

  public Long getChangeAfterFilter() {
    if (USE_CHANGE_AFTER_FILTER && CHANGE_AFTER.length() > 0) {
      return Long.parseLong(CHANGE_AFTER);
    }
    return null;
  }

  public Date getDateAfterFilter() {
    return parseDate(DATE_AFTER);
  }

  public void setDateAfter(final Date value) {
    if (value == null) {
      DATE_AFTER = null;

    }
    else {
      DATE_AFTER = DATE_FORMAT.format(value);
    }
  }

  protected List<Filter> createFilters() {
    final ArrayList<Filter> result = new ArrayList<Filter>();
    addDateFilter(USE_DATE_BEFORE_FILTER, getDateBefore(), result, true);
    addDateFilter(USE_DATE_AFTER_FILTER, getDateAfter(), result, false);

    if (USE_CHANGE_BEFORE_FILTER) {
      try {
        final long numBefore = Long.parseLong(CHANGE_BEFORE);
        result.add(new Filter() {
          public boolean accepts(RepositoryVersion change) {
            return change.getNumber() <= numBefore;
          }
        });
      }
      catch (NumberFormatException e) {
        //ignore
      }
    }

    if (USE_CHANGE_AFTER_FILTER) {
      try {
        final long numBefore = Long.parseLong(CHANGE_AFTER);
        result.add(new Filter() {
          public boolean accepts(RepositoryVersion change) {
            return change.getNumber() >= numBefore;
          }
        });
      }
      catch (NumberFormatException e) {
        //ignore
      }
    }

    return result;
  }

  private void addDateFilter(final boolean useFilter,
                             final Date date,
                             final ArrayList<Filter> result,
                             final boolean before) {
    if (useFilter) {
      result.add(new Filter() {
        public boolean accepts(RepositoryVersion change) {
          final Date changeDate = change.getDate();
          if (changeDate == null) return false;

          return before ? changeDate.before(date) : changeDate.after(date);
        }
      });
    }
  }

  private Filter createFilter() {
    final List<Filter> filters = createFilters();
    return new Filter() {
      public boolean accepts(RepositoryVersion change) {
        for (Filter filter : filters) {
          if (!filter.accepts(change)) return false;
        }
        return true;
      }
    };
  }

  public void filterChanges(final List<RepositoryVersion> changeListInfos) {
    Filter filter = createFilter();
    for (Iterator<RepositoryVersion> iterator = changeListInfos.iterator(); iterator.hasNext();) {
      RepositoryVersion changeListInfo = iterator.next();
      if (!filter.accepts(changeListInfo)) {
        iterator.remove();
      }
    }
  }

  public String getUserFilter() {
    if (USE_USER_FILTER) {
      return USER;
    }
    else {
      return null;
    }
  }


}
