// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;

@ApiStatus.Internal
@State(name = "DateTimeFormatter", storages = @Storage("ui-datetime.xml"), category = SettingsCategory.SYSTEM)
public final class DateTimeFormatManager implements PersistentStateComponent<DateTimeFormatManager> {
  private static final String DEFAULT_DATE_FORMAT = "dd MMM yyyy";

  private boolean myOverrideSystemDateFormat = false;
  private boolean myUse24HourTime = true;
  private String myPattern = DEFAULT_DATE_FORMAT;
  private boolean myPrettyFormattingAllowed = true;

  @Override
  public DateTimeFormatManager getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull DateTimeFormatManager state) {
    XmlSerializerUtil.copyBean(state, this);
    resetFormats();
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

  public @NotNull String getDateFormatPattern() {
    return myPattern;
  }

  public void setDateFormatPattern(@NotNull String pattern) {
    try {
      //noinspection ResultOfObjectAllocationIgnored
      new SimpleDateFormat(pattern);
      myPattern = pattern;
    }
    catch (Exception ignored) { }
  }

  public boolean isPrettyFormattingAllowed() {
    return myPrettyFormattingAllowed;
  }

  public void setPrettyFormattingAllowed(boolean prettyFormattingAllowed) {
    myPrettyFormattingAllowed = prettyFormattingAllowed;
  }

  public static DateTimeFormatManager getInstance() {
    return ApplicationManager.getApplication().getService(DateTimeFormatManager.class);
  }

  //<editor-fold desc="Internal stuff">
  record Formats(
    DateTimeFormatter date, DateTimeFormatter timeShort, DateTimeFormatter timeMedium, DateTimeFormatter dateTime,
    DateFormat dateFmt, DateFormat dateTimeFmt
  ) { }

  private volatile Formats myFormats;

  Formats getFormats() {
    var formats = myFormats;
    if (formats == null) {
      myFormats = formats = myOverrideSystemDateFormat ? DateFormatUtil.getCustomFormats(this) : DateFormatUtil.getSystemFormats();
    }
    return formats;
  }

  @ApiStatus.Internal
  public @NotNull DateFormat getDateFormat() {
    return getFormats().dateFmt;
  }

  @ApiStatus.Internal
  public void resetFormats() {
    myFormats = null;
  }
  //</editor-fold>
}
