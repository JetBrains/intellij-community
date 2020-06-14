// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 */
@State(name = "DateTimeFormatter", storages = @Storage("ui-datetime.xml"))
public class DateTimeFormatManager implements PersistentStateComponent<Element> {
  public static final String DEFAULT_DATE_FORMAT = "dd MMM yyyy";
  private boolean myPrettyFormattingAllowed = true;
  private String myPattern = DEFAULT_DATE_FORMAT;
  private boolean myOverrideSystemDateFormat = false;
  private boolean myUse24HourTime = true;

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

  public boolean isOverrideSystemDateFormat() {
    return myOverrideSystemDateFormat;
  }

  public void setOverrideSystemDateFormat(boolean overrideSystemDateFormat) {
    myOverrideSystemDateFormat = overrideSystemDateFormat;
  }

  public boolean isUse24HourTime() {
    return myUse24HourTime;
  }

  public void setUse24HourTime(boolean use24HourTime) {
    myUse24HourTime = use24HourTime;
  }

  public void setPrettyFormattingAllowed(boolean prettyFormattingAllowed) {
    myPrettyFormattingAllowed = prettyFormattingAllowed;
  }

  @Nullable
  public DateFormat getDateFormat() {
    try {
      return new SimpleDateFormat(myPattern);
    }
    catch (IllegalArgumentException e) {
      e.printStackTrace();
    }
    return null;
  }

  public Set<String> getIds() {
    return DateTimeFormatterBean.EP_NAME.getExtensionList().stream().map(bean -> bean.id).collect(Collectors.toSet());
  }

  @NotNull
  public String getDateFormatPattern() {
    return myPattern;
  }

  public void setDateFormatPattern(@NotNull String pattern) {
    try {
      new SimpleDateFormat(pattern);
      myPattern = pattern;
    } catch (Exception ignored) {
    }
  }

  public boolean isPrettyFormattingAllowed() {
    return myPrettyFormattingAllowed;
  }

  public static DateTimeFormatManager getInstance() {
    return ServiceManager.getService(DateTimeFormatManager.class);
  }
}
