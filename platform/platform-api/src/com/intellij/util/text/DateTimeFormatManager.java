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
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 */
@State(name = "DateTimeFormatter", storages = @Storage("ui-datetime.xml"))
public class DateTimeFormatManager implements PersistentStateComponent<Element> {
  private boolean myPrettyFormattingAllowed = true;
  private HashMap<String, String> myPatterns = new HashMap<>();
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

  public void setPrettyFormattingAllowed(boolean prettyFormattingAllowed) {
    myPrettyFormattingAllowed = prettyFormattingAllowed;
  }

  @Nullable
  public DateFormat getDateFormat(@NotNull String formatterID) {
    String pattern = myPatterns.get(formatterID);
    if (pattern == null) {
      for (DateTimeFormatterBean formatterBean : DateTimeFormatterBean.EP_NAME.getExtensions()) {
        if (formatterBean.id.equals(formatterID)) {
          if (!StringUtil.isEmpty(formatterBean.format)) {
            pattern = formatterBean.format;
          }
        }
      }
    }

    if (pattern != null) {
      try {
        return new SimpleDateFormat(pattern);
      }
      catch (IllegalArgumentException e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  public Set<String> getIds() {
    return DateTimeFormatterBean.EP_NAME.getExtensionList().stream().map(bean -> bean.id).collect(Collectors.toSet());
  }

  @Nullable
  public String getDateFormatPattern(String formatterID) {
    return myPatterns.get(formatterID);
  }

  public void setDateFormatPattern(String formatterID, @Nullable String pattern) {
    //assert myPatterns.containsKey(formatterID) : "Unknown formatterID: " + formatterID
    if (StringUtil.isEmpty(pattern)) {
      myPatterns.remove(formatterID);
    } else {
      myPatterns.put(formatterID, pattern);
    }
  }

  public boolean isPrettyFormattingAllowed() {
    return myPrettyFormattingAllowed;
  }

  public static DateTimeFormatManager getInstance() {
    return ServiceManager.getService(DateTimeFormatManager.class);
  }

  public HashMap<String, String> getPatterns() {
    return myPatterns;
  }

  public void setPatterns(HashMap<String, String> patterns) {
    myPatterns = patterns;
  }
}
