// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.editor;

import com.intellij.openapi.diagnostic.Logger;

public class PropertiesGetter {
  private static final Logger logger = Logger.getInstance(PropertiesGetter.class);

  private static final String[] PAGE_SIZE_PROPERTY_NAMES = {"lfe.pageSize", "lfe.ps"};
  private static final String[] MAX_PAGE_BORDER_SHIFT_PROPERTY_NAMES = {"lfe.maxPageBorderShift", "lfe.mpbs"};
  private static final String[] CHANGE_PAGE_INVISIBLE_DELAY_PROPERTY_NAMES = {"lfe.changePageInvisibleDelay", "lfe.cpid"};
  private static final String[] EXPERIMENTAL_MODE_PROPERTY_NAMES = {"lfe.experimentalMode", "lfe.em"};

  private static final int DEFAULT_PAGE_SIZE_BYTES = 100_000;
  private static final int DEFAULT_MAX_PAGE_BORDER_SHIFT_BYTES = 1_000;
  private static final int CHANGE_PAGE_INVISIBLE_DELAY_MS = 100;
  private static final boolean DEFAULT_IS_EXPERIMENTAL_MODE_ON = false;


  public static int getPageSize() {
    return getPropertyInt(PAGE_SIZE_PROPERTY_NAMES, DEFAULT_PAGE_SIZE_BYTES);
  }

  public static int getMaxPageBorderShiftBytes() {
    return getPropertyInt(MAX_PAGE_BORDER_SHIFT_PROPERTY_NAMES, DEFAULT_MAX_PAGE_BORDER_SHIFT_BYTES);
  }

  public static int getChangePageInvisibleDelayMs() {
    return getPropertyInt(CHANGE_PAGE_INVISIBLE_DELAY_PROPERTY_NAMES, CHANGE_PAGE_INVISIBLE_DELAY_MS);
  }

  public static boolean getIsExperimentalModeOn() {
    return getPropertyBoolean(EXPERIMENTAL_MODE_PROPERTY_NAMES, DEFAULT_IS_EXPERIMENTAL_MODE_ON);
  }

  private static int getPropertyInt(String[] propertyNames, int defaultValue) {
    String strValue;
    for (String propertyName : propertyNames) {
      strValue = System.getProperty(propertyName);
      if (strValue != null) {
        try {
          return Integer.parseInt(strValue);
        }
        catch (NumberFormatException e) {
          logger.warn("NumberFormatException: can't parse to int [propertyName="
                      + propertyName + " stringValue=" + strValue + " defaultValue=" + defaultValue + ")");
          return defaultValue;
        }
      }
    }
    return defaultValue;
  }

  @SuppressWarnings("SameParameterValue")
  private static boolean getPropertyBoolean(String[] propertyNames, boolean defaultValue) {
    final String[] positiveStrs = {"1", "true", "t", "yes", "y", "+"};
    final String[] negativeStrs = {"0", "-1", "false", "f", "no", "n", "-"};
    String strValue;
    for (String propertyName : propertyNames) {
      strValue = System.getProperty(propertyName);
      if (strValue != null) {

        for (String word : positiveStrs) {
          if (strValue.equalsIgnoreCase(word)) {
            return true;
          }
        }

        for (String word : negativeStrs) {
          if (strValue.equalsIgnoreCase(word)) {
            return false;
          }
        }

        logger.warn("Format problem: can't parse to boolean [propertyName="
                    + propertyName + " stringValue=" + strValue + " defaultValue=" + defaultValue + ")");
        return defaultValue;
      }
    }
    return defaultValue;
  }
}
