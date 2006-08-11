/**
 * @author cdr
 */
package com.intellij.testFramework;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
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
import java.awt.*;

public class ExpectedHighlightingData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.ExpectedHighlightingData");

 @NonNls private static final String ERROR_MARKER = "error";
 @NonNls private static final String WARNING_MARKER = "warning";
 @NonNls private static final String INFORMATION_MARKER = "weak_warning";
 @NonNls private static final String SERVER_PROBLEM_MARKER = "server_problem";
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
    this(document, checkWarnings, false, checkInfos, false);
  }

  public ExpectedHighlightingData(Document document, boolean checkWarnings, boolean checkWeakWarnings, boolean checkInfos){
    this(document, checkWarnings, checkWeakWarnings, checkInfos, false);
  }

  public ExpectedHighlightingData(Document document,
                                  boolean checkWarnings,
                                  boolean checkWeakWarnings,
                                  boolean checkInfos,
                                  boolean checkServerProblems) {
    highlightingTypes = new THashMap<String,ExpectedHighlightingSet>();
    highlightingTypes.put(ERROR_MARKER, new ExpectedHighlightingSet(ERROR_MARKER, HighlightInfoType.ERROR, HighlightSeverity.ERROR, false, true));
    highlightingTypes.put(WARNING_MARKER, new ExpectedHighlightingSet(WARNING_MARKER, HighlightInfoType.UNUSED_SYMBOL, HighlightSeverity.WARNING, false, checkWarnings));
    highlightingTypes.put(INFORMATION_MARKER, new ExpectedHighlightingSet(INFORMATION_MARKER, HighlightInfoType.INFO, HighlightSeverity.INFO, false, checkWeakWarnings));
    highlightingTypes.put(SERVER_PROBLEM_MARKER, new ExpectedHighlightingSet(SERVER_PROBLEM_MARKER, HighlightInfoType.GENERIC_WARNINGS_OR_ERRORS_FROM_SERVER, HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING, false, checkServerProblems));
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
    @NonNls String pat = ".*?(<(" + typesRegex + ")(?: descr=\\\"((?:[^\\\"\\\\]|\\\\\\\")*)\\\")?(?: type=\\\"([0-9A-Z_]+)\\\")?(?: foreground=\\\"([0-9xa-f]+)\\\")?(?: background=\\\"([0-9xa-f]+)\\\")?(?: effectcolor=\\\"([0-9xa-f]+)\\\")?(?: effecttype=\\\"([A-Z]+)\\\")?(?: fonttype=\\\"([0-9]+)\\\")?(/)?>)(.*)";
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
      String foregroundColor = m.group(5);
      String backgroundColor = m.group(6);
      String effectColor = m.group(7);
      String effectType = m.group(8);
      String fontType = m.group(9);
      String closeTagMarker = m.group(10);
      String rest = m.group(11);

      String content;
      int endOffset;
      if (closeTagMarker == null) {
        Pattern pat2 = Pattern.compile("(.*?)</" + marker + ">(.*)", Pattern.DOTALL);
        final Matcher matcher2 = pat2.matcher(rest);
        LOG.assertTrue(matcher2.matches());
        content = matcher2.group(1);
        endOffset = m.start(11) + matcher2.start(2);
      }
      else {
        // <XXX/>
        content = "";
        endOffset = m.start(11);
      }

      document.replaceString(startOffset, endOffset, content);
      TextAttributes forcedAttributes = null;
      if (foregroundColor != null) {
        forcedAttributes = new TextAttributes(Color.decode(foregroundColor), Color.decode(backgroundColor),
                                                              Color.decode(effectColor), EffectType.valueOf(effectType),
                                                              Integer.parseInt(fontType));
      }

      TextRange textRange = new TextRange(startOffset, startOffset + content.length());
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(expectedHighlightingSet.defaultErrorType, textRange, descr, forcedAttributes);

      HighlightInfoType type = null;

      if (typeString != null) {
        try {
          Field field = HighlightInfoType.class.getField(typeString);
          type = (HighlightInfoType)field.get(null);
        }
        catch (Exception e) {
          LOG.error(e);
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
      if (!expectedInfosContainsInfo(info)) {
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

  private boolean expectedInfosContainsInfo(HighlightInfo info) {
    final Collection<ExpectedHighlightingSet> expectedHighlights = highlightingTypes.values();
    for (ExpectedHighlightingSet highlightingSet : expectedHighlights) {
      if (highlightingSet.severity != info.getSeverity()) continue;
      if (!highlightingSet.enabled) return true;
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
      (expectedInfo.type == null || expectedInfo.type.equals(info.type)) &&
      (Comparing.strEqual("*", expectedInfo.description) || Comparing.strEqual(info.description, expectedInfo.description))
      && (expectedInfo.forcedTextAttributes == null || expectedInfo.getTextAttributes().equals(info.getTextAttributes()))
      ;
  }
}