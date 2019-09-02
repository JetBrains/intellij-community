// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
@State(name = "DateTimeFormatter", storages = @Storage("ui-datetime.xml"))
public class DateTimeFormatManager implements PersistentStateComponent<Element> {
  private boolean myAllowPrettyFormattingGlobally = true;
  private HashMap<String, DateFormatPattern> myPatterns = new HashMap<>();
  @Nullable
  @Override
  public Element getState() {
    return XmlSerializer.serialize(this);
  }
  @Override
  public void loadState(@NotNull Element state) {
    DateTimeFormatManager loaded = XmlSerializer.deserialize(state, DateTimeFormatManager.class);
    XmlSerializerUtil.copyBean(loaded, this);
  }

  public void setAllowPrettyFormattingGlobally(boolean allowPrettyFormattingGlobally) {
    myAllowPrettyFormattingGlobally = allowPrettyFormattingGlobally;
  }

  @Nullable
  public DateFormat getDateFormat(@NotNull String formatterID) {
    DateFormatPattern pattern = myPatterns.get(formatterID);
    if (pattern == null) {
      for (DateTimeFormatterBean formatterBean : DateTimeFormatterBean.EP_NAME.getExtensions()) {
        if (formatterBean.id.equals(formatterID)) {
          if (!StringUtil.isEmpty(formatterBean.format)) {
            pattern = new DateFormatPattern(formatterBean.format);
          }
        }
      }
    }

    if (pattern != null) {
      try {
        return new SimpleDateFormat(pattern.myFormat);
      }
      catch (IllegalArgumentException e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  public Set<String> getIds() {
    return myPatterns.keySet();
  }

  @Nullable
  public String getDateFormatPattern(String formatterID) {
    DateFormatPattern pattern = myPatterns.get(formatterID);
    return pattern == null ? null : pattern.myFormat;
  }

  public boolean isPrettyFormattingAllowed() {
    return myAllowPrettyFormattingGlobally;
  }

  public static DateTimeFormatManager getInstance() {
    return ServiceManager.getService(DateTimeFormatManager.class);
  }

  private static class DateFormatPattern {
    private final String myFormat;

    private DateFormatPattern(String format) {
      myFormat = format;
    }
  }
}
