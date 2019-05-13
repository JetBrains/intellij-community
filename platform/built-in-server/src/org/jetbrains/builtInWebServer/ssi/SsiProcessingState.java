/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.builtInWebServer.ssi;

import com.google.common.escape.Escaper;
import com.google.common.html.HtmlEscapers;
import com.google.common.net.PercentEscaper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class SsiProcessingState {
  protected static final String DEFAULT_CONFIG_ERR_MSG = "[an error occurred while processing this directive]";
  protected static final String DEFAULT_CONFIG_TIME_FMT = "%A, %d-%b-%Y %T %Z";
  protected static final String DEFAULT_CONFIG_SIZE_FMT = "abbrev";
  // encode only the same characters that apache does
  protected static final Escaper urlEscaper = new PercentEscaper(",:-_.*/!~'()", false);
  protected String configErrorMessage = DEFAULT_CONFIG_ERR_MSG;
  protected String configTimeFmt = DEFAULT_CONFIG_TIME_FMT;
  protected String configSizeFmt = DEFAULT_CONFIG_SIZE_FMT;
  protected final SsiExternalResolver ssiExternalResolver;
  protected final long lastModifiedDate;
  protected Strftime strftime;
  protected final SsiConditionalState conditionalState = new SsiConditionalState();

  private boolean alreadySet;

  public SsiProcessingState(@NotNull SsiExternalResolver ssiExternalResolver, long lastModifiedDate) {
    this.ssiExternalResolver = ssiExternalResolver;
    this.lastModifiedDate = lastModifiedDate;
    setConfigTimeFormat(DEFAULT_CONFIG_TIME_FMT, true);
  }

  static class SsiConditionalState {
    /**
     * Set to true if the current conditional has already been completed, i.e.:
     * a branch was taken.
     */
    boolean branchTaken = false;
    /**
     * Counts the number of nested false branches.
     */
    int nestingCount = 0;
    /**
     * Set to true if only conditional commands ( if, elif, else, endif )
     * should be processed.
     */
    boolean processConditionalCommandsOnly = false;
  }

  public void setConfigTimeFormat(String configTimeFmt, boolean fromConstructor) {
    this.configTimeFmt = configTimeFmt;
    this.strftime = new Strftime(configTimeFmt, Locale.US);
    setDateVariables(fromConstructor);
  }

  public String getVariableValue(String variableName) {
    return getVariableValue(variableName, "none");
  }

  public String getVariableValue(@NotNull String variableName, String encoding) {
    String variableValue = ssiExternalResolver.getVariableValue(variableName);
    return variableValue == null ? null : encode(variableValue, encoding);
  }

  /**
   * Applies variable substitution to the specified String and returns the
   * new resolved string.
   */
  @SuppressWarnings("AssignmentToForLoopParameter")
  public String substituteVariables(String val) {
    // If it has no references or HTML entities then no work
    // need to be done
    if (val.indexOf('$') < 0 && val.indexOf('&') < 0) {
      return val;
    }

    val = val.replace("&lt;", "<");
    val = val.replace("&gt;", ">");
    val = val.replace("&quot;", "\"");
    val = val.replace("&amp;", "&");

    StringBuilder sb = new StringBuilder(val);
    int charStart = sb.indexOf("&#");
    while (charStart > -1) {
      int charEnd = sb.indexOf(";", charStart);
      if (charEnd > -1) {
        char c = (char)Integer.parseInt(
          sb.substring(charStart + 2, charEnd));
        sb.delete(charStart, charEnd + 1);
        sb.insert(charStart, c);
        charStart = sb.indexOf("&#");
      }
      else {
        break;
      }
    }

    for (int i = 0; i < sb.length(); ) {
      // Find the next $
      for (; i < sb.length(); i++) {
        if (sb.charAt(i) == '$') {
          i++;
          break;
        }
      }
      if (i == sb.length()) break;
      // Check to see if the $ is escaped
      if (i > 1 && sb.charAt(i - 2) == '\\') {
        sb.deleteCharAt(i - 2);
        i--;
        continue;
      }
      int nameStart = i;
      int start = i - 1;
      int end;
      int nameEnd;
      char endChar = ' ';
      // Check for {} wrapped var
      if (sb.charAt(i) == '{') {
        nameStart++;
        endChar = '}';
      }
      // Find the end of the var reference
      for (; i < sb.length(); i++) {
        if (sb.charAt(i) == endChar) break;
      }
      end = i;
      nameEnd = end;
      if (endChar == '}') end++;
      // We should now have enough to extract the var name
      String varName = sb.substring(nameStart, nameEnd);
      String value = getVariableValue(varName);
      if (value == null) value = "";
      // Replace the var name with its value
      sb.replace(start, end, value);
      // Start searching for the next $ after the value
      // that was just substituted.
      i = start + value.length();
    }
    return sb.toString();
  }

  protected void setDateVariables(boolean fromConstructor) {
    if (fromConstructor && alreadySet) {
      return;
    }

    alreadySet = true;
    Date date = new Date();
    TimeZone timeZone = TimeZone.getTimeZone("GMT");
    ssiExternalResolver.setVariableValue("DATE_GMT", formatDate(date, timeZone));
    ssiExternalResolver.setVariableValue("DATE_LOCAL", formatDate(date, null));
    ssiExternalResolver.setVariableValue("LAST_MODIFIED", formatDate(new Date(lastModifiedDate), null));
  }

  @NotNull
  protected String formatDate(@NotNull Date date, @Nullable TimeZone timeZone) {
    if (timeZone == null) {
      return strftime.format(date);
    }
    else {
      TimeZone oldTimeZone = strftime.getTimeZone();
      strftime.setTimeZone(timeZone);
      String retVal = strftime.format(date);
      strftime.setTimeZone(oldTimeZone);
      return retVal;
    }
  }

  @NotNull
  protected String encode(@NotNull String value, @NotNull String encoding) {
    if (encoding.equalsIgnoreCase("url")) {
      return urlEscaper.escape(value);
    }
    else if (encoding.equalsIgnoreCase("none")) {
      return value;
    }
    else if (encoding.equalsIgnoreCase("entity")) {
      return HtmlEscapers.htmlEscaper().escape(value);
    }
    else {
      throw new IllegalArgumentException("Unknown encoding: " + encoding);
    }
  }
}