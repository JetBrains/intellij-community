package com.intellij.localvcs.integration.ui.models;

import java.text.DateFormat;
import java.util.Date;

public class FormatUtil {
  public static String formatTimestamp(long t) {
    DateFormat f = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    return f.format(new Date(t));
  }
}
