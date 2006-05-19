/**
 * @author cdr
 */
package com.intellij.testFramework;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpectedHighlightingData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.ExpectedHighlightingData");

 @NonNls private static final String ERROR_MARKER = "error";
 @NonNls private static final String WARNING_MARKER = "warning";
 @NonNls private static final String INFORMATION_MARKER = "weak_warning";
 @NonNls private static final String INFO_MARKER = "info";
 @NonNls private static final String END_LINE_HIGHLIGHT_MARKER = "EOLError";
 @NonNls private static final String END_LINE_WARNING_MARKER = "EOLWarning";

  static class ExpectedHighlightingSet {
    public final String marker;
    private final boolean endOfLine;
    final boolean enabled;
    Set<HighlightInfo> infos;
    HighlightInfoType defaultErrorType;
    HighlightSeverity severity;

    public ExpectedHighlightingSet(String marker, HighlightInfoType defaultErrorType,HighlightSeverity severity, boolean endOfLine, boolean enabled) {
      this.marker = marker;
      this.endOfLine = endOfLine;
      this.enabled = enabled;
      infos = new THashSet<HighlightInfo>();
      this.defaultErrorType = defaultErrorType;
      this.severity = severity;
    }
  }
  Map<String,ExpectedHighlightingSet> highlightingTypes;

  public ExpectedHighlightingData(Document document,boolean checkWarnings, boolean checkInfos) {
    this(document, checkWarnings, false, checkInfos);
  }

  public ExpectedHighlightingData(Document document,boolean checkWarnings,boolean checkWeakWarnings, boolean checkInfos) {
    highlightingTypes = new THashMap<String,ExpectedHighlightingSet>();
    highlightingTypes.put(ERROR_MARKER, new ExpectedHighlightingSet(ERROR_MARKER, HighlightInfoType.ERROR, HighlightSeverity.ERROR, false, true));
    highlightingTypes.put(WARNING_MARKER, new ExpectedHighlightingSet(WARNING_MARKER, HighlightInfoType.UNUSED_SYMBOL, HighlightSeverity.WARNING, false, checkWarnings));
    highlightingTypes.put(INFORMATION_MARKER, new ExpectedHighlightingSet(INFORMATION_MARKER, HighlightInfoType.INFO, HighlightSeverity.INFO, false, checkWeakWarnings));
    highlightingTypes.put(INFO_MARKER, new ExpectedHighlightingSet(INFO_MARKER, HighlightInfoType.TODO, HighlightSeverity.INFORMATION, false, checkInfos));
    highlightingTypes.put(END_LINE_HIGHLIGHT_MARKER, new ExpectedHighlightingSet(END_LINE_HIGHLIGHT_MARKER, HighlightInfoType.ERROR, HighlightSeverity.ERROR, true, true));
    highlightingTypes.put(END_LINE_WARNING_MARKER, new ExpectedHighlightingSet(END_LINE_WARNING_MARKER, HighlightInfoType.WARNING, HighlightSeverity.WARNING, true, checkWarnings));
    extractExpectedHighlightsSet(document);
  }

  /**
   * remove highlights (bounded with <marker>...</marker>) from test case file
   * @param document
   */
  private void extractExpectedHighlightsSet(Document document) {
    String text = document.getText();

    String typesRegex = "";
    for (String marker : highlightingTypes.keySet()) {
      typesRegex += (typesRegex.length() == 0 ? "" : "|") + "(?:" + marker + ")";
    }

    // er...
    // any code then <marker> (with optional descr="...") then any code then </marker> then any code
    String pat = ".*?(<(" + typesRegex + ")(?: descr=\\\"((?:[^\\\"\\\\]|\\\\\\\")*)\\\")?(?: type=\\\"([0-9A-Z_]+)\\\")?(/)?>)(.*)";
                 //"(.+?)</" + marker + ">).*";
    Pattern p = Pattern.compile(pat, Pattern.DOTALL);
    for (; ;) {
      Matcher m = p.matcher(text);
      if (!m.matches()) break;
      int startOffset = m.start(1);
      String marker = m.group(2);
      final ExpectedHighlightingSet expectedHighlightingSet = highlightingTypes.get(marker);

      @NonNls String descr = m.group(3);
      if (descr == null) {
        // no descr means any string by default
        descr = "*";
      }
      else if (descr.equals("null")) {
        // explicit "null" descr
        descr = null;
      }

      String typeString = m.group(4);
      String closeTagMarker = m.group(5);
      String rest = m.group(6);

      String content;
      int endOffset;
      if (closeTagMarker == null) {
        Pattern pat2 = Pattern.compile("(.*?)</" + marker + ">(.*)", Pattern.DOTALL);
        final Matcher matcher2 = pat2.matcher(rest);
        LOG.assertTrue(matcher2.matches());
        content = matcher2.group(1);
        endOffset = m.start(6) + matcher2.start(2);
      }
      else {
        // <XXX/>
        content = "";
        endOffset = m.start(6);
      }

      document.replaceString(startOffset, endOffset, content);

      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(expectedHighlightingSet.defaultErrorType, startOffset, startOffset + content.length(), descr);

      HighlightInfoType type = null;

      if (typeString != null) {
        Field[] fields = HighlightInfoType.class.getFields();
        for (Field field : fields) {
          try {
            if (field.getName().equals(typeString)) type = (HighlightInfoType)field.get(null);
          }
          catch (Exception e) {
          }
        }

        if (type == null) LOG.assertTrue(false,"Wrong highlight type: " + typeString);
      }

      highlightInfo.type = type;
      highlightInfo.isAfterEndOfLine = expectedHighlightingSet.endOfLine;
      LOG.assertTrue(expectedHighlightingSet.enabled);
      expectedHighlightingSet.infos.add(highlightInfo);
      text = document.getText();
    }
  }

  public Collection<HighlightInfo> getExtractedHighlightInfos(){
    final Collection<HighlightInfo> result = new ArrayList<HighlightInfo>();
    final Collection<ExpectedHighlightingSet> collection = highlightingTypes.values();
    for (ExpectedHighlightingSet set : collection) {
      result.addAll(set.infos);
    }
    return result;
  }

  public void checkResult(Collection<HighlightInfo> infos, String text) {
    for (HighlightInfo info : infos) {
      if (!expectedInfosContainsInfo(this, info)) {
        final int startOffset = info.startOffset;
        final int endOffset = info.endOffset;
        String s = text.substring(startOffset, endOffset);
        String desc = info.description;

        int y1 = StringUtil.offsetToLineNumber(text, startOffset);
        int y2 = StringUtil.offsetToLineNumber(text, endOffset);
        int x1 = startOffset - StringUtil.lineColToOffset(text, y1, 0);
        int x2 = endOffset - StringUtil.lineColToOffset(text, y2, 0);

        Assert.assertTrue("Extra text fragment highlighted " +
                          "(" + (x1 + 1) + ", " + (y1 + 1) + ")" + "-" +
                          "(" + (x2 + 1) + ", " + (y2 + 1) + ")" +
                          " :'" +
                          s +
                          "'" + (desc == null ? "" : " (" + desc + ")")
                          + " [" + info.type + "]",
                          false);
      }
    }
    final Collection<ExpectedHighlightingSet> expectedHighlights = highlightingTypes.values();
    for (ExpectedHighlightingSet highlightingSet : expectedHighlights) {
      final Set<HighlightInfo> expInfos = highlightingSet.infos;
      for (HighlightInfo expectedInfo : expInfos) {
        if (!infosContainsExpectedInfo(infos, expectedInfo) && highlightingSet.enabled) {
          final int startOffset = expectedInfo.startOffset;
          final int endOffset = expectedInfo.endOffset;
          String s = text.substring(startOffset, endOffset);
          String desc = expectedInfo.description;

          int y1 = StringUtil.offsetToLineNumber(text, startOffset);
          int y2 = StringUtil.offsetToLineNumber(text, endOffset);
          int x1 = startOffset - StringUtil.lineColToOffset(text, y1, 0);
          int x2 = endOffset - StringUtil.lineColToOffset(text, y2, 0);

          Assert.assertTrue("Text fragment was not highlighted " +
                            "(" + (x1 + 1) + ", " + (y1 + 1) + ")" + "-" +
                            "(" + (x2 + 1) + ", " + (y2 + 1) + ")" +
                            " :'" +
                            s +
                            "'" + (desc == null ? "" : " (" + desc + ")"),
                            false);
        }
      }
    }

  }

  private static boolean infosContainsExpectedInfo(Collection<HighlightInfo> infos, HighlightInfo expectedInfo) {
    for (HighlightInfo info : infos) {
      if (infoEquals(expectedInfo, info)) {
        return true;
      }
    }
    return false;
  }

  private static boolean expectedInfosContainsInfo(ExpectedHighlightingData expectedHighlightsSet, HighlightInfo info) {
    final Collection<ExpectedHighlightingSet> expectedHighlights = expectedHighlightsSet.highlightingTypes.values();
    for (ExpectedHighlightingSet highlightingSet : expectedHighlights) {
      if (highlightingSet.severity == info.getSeverity() && !highlightingSet.enabled) return true;
      final Set<HighlightInfo> infos = highlightingSet.infos;
      for (HighlightInfo expectedInfo : infos) {
        if (infoEquals(expectedInfo, info)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean infoEquals(HighlightInfo expectedInfo, HighlightInfo info) {
    if (expectedInfo == info) return true;
    return
      info.getSeverity() == expectedInfo.getSeverity() &&
      info.startOffset + (info.isAfterEndOfLine ? 1 : 0) == expectedInfo.startOffset &&
      info.endOffset == expectedInfo.endOffset &&
      info.isAfterEndOfLine == expectedInfo.isAfterEndOfLine &&
      (expectedInfo.type == null || info.type == expectedInfo.type) &&

      (Comparing.strEqual("*",expectedInfo.description) ? true :
                                                        expectedInfo.description == null || info.description == null ? info.description == expectedInfo.description :
                                                          Comparing.strEqual(info.description,expectedInfo.description));
  }
}