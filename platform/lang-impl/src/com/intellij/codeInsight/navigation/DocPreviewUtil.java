/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides utility methods for building documentation preview.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 7/10/12 8:06 AM
 */
public class DocPreviewUtil {

  private static final Set<String> TAGS_TO_ADD_LF = new HashSet<String>();
  private static final Set<String> TAGS_TO_IGNORE = new HashSet<String>();
  static {
    for (String tag : new String[] {"p", "blockquote", "pre"}) {
      TAGS_TO_ADD_LF.add(tag);
      TAGS_TO_ADD_LF.add(tag.toUpperCase());
    }

    for (String tag : new String[] {"style", "b", "small"}) {
      TAGS_TO_IGNORE.add(tag);
      TAGS_TO_IGNORE.add(tag.toUpperCase());
    }
  }

  private DocPreviewUtil() {
  }

  /**
   * Allows to build a documentation preview from the given arguments. Basically, takes given 'full documentation', cuts it according
   * to the given 'desired rows' argument and returns a result.
   * 
   * @param header                     target documentation header. Is expected to be a result of the
   *                                   {@link DocumentationProvider#getQuickNavigateInfo(PsiElement, PsiElement)} call
   * @param qName                      there is a possible case that not all documentation text will be included to the preview
   *                                   (according to the given 'desired rows and columns per-row' arguments). A link that points to the
   *                                   element with the given qualified name is added to the preview's end if the qName is provided then
   * @param fullText                   full documentation text (if available)
   * @param desiredRowsNumber          maximum number of rows to use at the preview's body ('header' text is not count here)
   * @return                           preview text to use for the given arguments
   */
  @NotNull
  public static String buildPreview(@NotNull String header, @Nullable String qName, @Nullable String fullText, int desiredRowsNumber) {
    if (fullText == null) {
      return header;
    }
    
    int bodyStart = fullText.indexOf("<body>");
    if (bodyStart < 0) {
      return header;
    }
    bodyStart += "<body>".length();
    
    int bodyEnd = fullText.indexOf("</body>");
    if (bodyEnd < 0) {
      return header;
    }
    
    int headerEnd = fullText.indexOf("</PRE>");
    if (headerEnd < 0) {
      return header;
    }
    
    String headerWithLinks = fullText.substring(bodyStart, headerEnd);
    String docText = fullText.substring(headerEnd + "</PRE>".length(), bodyEnd);

    // The algorithm is:
    //   1. Get given header text and replace meaningful symbols with the links to those symbols;
    //   2. Calculate max symbols per-row to use for the result as a number of non-markup symbols at the longest header line;
    //   3. Process full text body as follows:
    //     2.1. Count non-markup symbols until desired row symbols number is exceeded;
    //     2.2. Insert <br> after that to start a new row (if max rows number is not reached);
    //     3.3. Stop processing as soon as the desired rows number is reached or the text is finished;
    //   4. Add a link to the full documentation if it's not placed inside the resulted text;
    //   5. Add closing tags for all non-matched open tags;

    final Context context = new Context(desiredRowsNumber);
    
    int columnsPerRow = parseHeader(header, headerWithLinks, qName, context);
    process(docText, new BodyCallback(context, qName, columnsPerRow));
    
    //region Add closing tags
    while (!context.openTags.isEmpty()) {
      context.buffer.append("</").append(context.openTags.pop()).append('>');
    }
    //endregion
    
    return context.buffer.toString();
  }

  private static int parseHeader(@NotNull String headerTemplate,
                                 @NotNull String headerWithLinks,
                                 @Nullable String qName,
                                 @NotNull Context context)
  {

    //region Build links info.
    Map<String/*qName*/, String/*address*/> links = new HashMap<String, String>();
    process(headerWithLinks, new LinksCollector(links));
    if (qName != null) {
      links.put(qName, DocumentationManager.PSI_ELEMENT_PROTOCOL + qName);
    }
    //endregion


    //region Apply links info to the header template.
    String headerToUse = headerTemplate.replace("\n", "<br/>");
    for (Map.Entry<String, String> entry : links.entrySet()) {
      String visibleName = entry.getKey();
      int i = visibleName.lastIndexOf('.');
      if (i > 0 && i < visibleName.length() - 1) {
        visibleName = visibleName.substring(i + 1);
      }
      headerToUse = headerToUse.replace(entry.getKey(), String.format("<a href=\"%s\">%s</a>", entry.getValue(), visibleName));
    }
    context.buffer.append(headerToUse);
    //endregion

    //region Update 'columns-per-row' if the header is wide.
    MaxColumnCalculator calculator = new MaxColumnCalculator();
    process(headerToUse, calculator);
    return calculator.maxColumn;
    //endregion
  }

  private enum State {TEXT, INSIDE_OPEN_TAG, INSIDE_CLOSE_TAG}
  
  @SuppressWarnings("AssignmentToForLoopParameter")
  private static int process(@NotNull String text, @NotNull Callback callback) {
    State state = State.TEXT;
    int dataStartOffset = 0;
    int tagNameStartOffset = 0;
    String tagName = null;
    int i = 0;
    for (; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (state) {
        case TEXT:
          if (c == '<') {
            if (i > dataStartOffset) {
              if (!callback.onText(text.substring(dataStartOffset, i).replace("&nbsp;", " "))) {
                return dataStartOffset;
              }
            }
            dataStartOffset = i;
            if (i < text.length() - 1 && text.charAt(i + 1) == '/') {
              state = State.INSIDE_CLOSE_TAG;
              tagNameStartOffset = ++i + 1;
            }
            else {
              state = State.INSIDE_OPEN_TAG;
              tagNameStartOffset = i + 1;
            }
          }
          break;
        case INSIDE_OPEN_TAG:
          if (c == ' ') {
            tagName = text.substring(tagNameStartOffset, i);
          }
          else if (c == '/') {
            if (i < text.length() - 1 && text.charAt(i + 1) == '>') {
              if (tagName == null) {
                tagName = text.substring(tagNameStartOffset, i);
              }
              if (!callback.onStandaloneTag(tagName, text.substring(dataStartOffset, i + 2))) {
                return dataStartOffset;
              }
              tagName = null;
              state = State.TEXT;
              dataStartOffset = ++i + 1;
              break;
            }
          }
          else if (c == '>') {
            if (tagName == null) {
              tagName = text.substring(tagNameStartOffset, i);
            }
            if (!callback.onOpenTag(tagName, text.substring(dataStartOffset, i + 1))) {
              return dataStartOffset;
            }
            tagName = null;
            state = State.TEXT;
            dataStartOffset = i + 1;
          }
          break;
        case INSIDE_CLOSE_TAG:
          if (c == '>') {
            if (tagName == null) {
              tagName = text.substring(tagNameStartOffset, i);
            }
            if (!callback.onCloseTag(tagName, text.substring(dataStartOffset, i + 1))) {
              return dataStartOffset;
            }
            tagName = null;
            state = State.TEXT;
            dataStartOffset = i + 1;
          }
      }
    }

    if (dataStartOffset < text.length()) {
      callback.onText(text.substring(dataStartOffset, text.length()).replace("&nbsp;", " "));
    }
    
    return i;
  }

  private static class Context {

    @NotNull public final Stack<String> openTags = new Stack<String>();
    @NotNull public final StringBuilder buffer   = new StringBuilder();
    public final int rows;

    public Context(int rows) {
      this.rows = rows;
    }
  }

  private interface Callback {
    boolean onOpenTag(@NotNull String name, @NotNull String text);
    boolean onCloseTag(@NotNull String name, @NotNull String text);
    boolean onStandaloneTag(@NotNull String name, @NotNull String text);
    boolean onText(@NotNull String text);
  }

  private static class LinksCollector implements Callback {

    private static final Pattern HREF_PATTERN = Pattern.compile("href=[\"']([^\"']+)");

    @NotNull private final Map<String, String> myLinks;
    private                String              myHref;

    LinksCollector(@NotNull Map<String, String> links) {
      myLinks = links;
    }

    @Override
    public boolean onOpenTag(@NotNull String name, @NotNull String text) {
      if (!"a".equals(name)) {
        return true;
      }
      Matcher matcher = HREF_PATTERN.matcher(text);
      if (matcher.find()) {
        myHref = matcher.group(1);
      }
      return true;
    }

    @Override
    public boolean onCloseTag(@NotNull String name, @NotNull String text) {
      if ("a".equals(name)) {
        myHref = null;
      }
      return true;
    }

    @Override
    public boolean onStandaloneTag(@NotNull String name, @NotNull String text) {
      return true;
    }

    @Override
    public boolean onText(@NotNull String text) {
      if (myHref != null) {
        myLinks.put(text, myHref);
        myHref = null;
      }
      return true;
    }
  }
  
  private static class MaxColumnCalculator implements Callback {

    private static final Map<String, String> SUBSTITUTIONS = new HashMap<String, String>();
    static {
      SUBSTITUTIONS.put("&lt;", "<");
      SUBSTITUTIONS.put("&gt;", ">");
      SUBSTITUTIONS.put("&nbsp;", " ");
    }

    public  int maxColumn;
    private int myCurrentColumn;

    @Override
    public boolean onOpenTag(@NotNull String name, @NotNull String text) {
      if ("br".equals(name)) {
        myCurrentColumn = 0;
      }
      return true;
    }

    @Override
    public boolean onCloseTag(@NotNull String name, @NotNull String text) {
      return true;
    }

    @Override
    public boolean onStandaloneTag(@NotNull String name, @NotNull String text) {
      return onOpenTag(name, text);
    }

    @Override
    public boolean onText(@NotNull String text) {
      for (Map.Entry<String, String> entry : SUBSTITUTIONS.entrySet()) {
        text = text.replace(entry.getKey(), entry.getValue());
      }
      myCurrentColumn += text.length();
      maxColumn = Math.max(maxColumn, myCurrentColumn);
      return true;
    }
  }

  private static class BodyCallback implements Callback {

    @NotNull protected final Context myContext;
    private                  boolean myScheduleNewLine;
    private                  boolean myInsidePre;
    private                  boolean myDocStarted;
    private                  int     myCurrentRow;
    private                  int     myCurrentColumn;
    @Nullable private        String  myQName;
    private                  int     myColumnsPerRow;


    protected BodyCallback(@NotNull Context context, @Nullable String qName, final int columnsPerRow) {
      myContext = context;
      myQName = qName;
      myColumnsPerRow = columnsPerRow;
    }

    public boolean onOpenTag(@NotNull String name, @NotNull String text) {
      if ("pre".equals(name)) {
        myInsidePre = true;
      }
      if (!processDelayedLfTag(name)) {
        return myCurrentRow < myContext.rows;
      }

      if (!TAGS_TO_IGNORE.contains(name)) {
        addText(text, false);
        myContext.openTags.push(name);
      }
      return true;
    }

    private boolean processDelayedLfTag(@NotNull String name) {
      if (!TAGS_TO_ADD_LF.contains(name)) {
        if (myScheduleNewLine) {
          newLine();
        }
        return true;
      }

      myScheduleNewLine = true;
      return false;
    }

    public boolean onCloseTag(@NotNull String name, @NotNull String text) {
      if ("pre".equals(name)) {
        myInsidePre = false;
      }

      if (!processDelayedLfTag(name)) {
        return myCurrentRow < myContext.rows;
      }

      if (!TAGS_TO_IGNORE.contains(name)) {
        addText(text, false);
        myContext.openTags.remove(name);
      }
      return true;
    }

    public boolean onStandaloneTag(@NotNull String name, @NotNull String text) {
      if (!processDelayedLfTag(name)) {
        return true;
      }

      if (!TAGS_TO_IGNORE.contains(name)) {
        addText(text, false);
      }
      return true;
    }

    public boolean onText(@NotNull String text) {
      boolean addSpace = false;
      if (!text.isEmpty() && (text.startsWith(" ") || text.startsWith("\t")) && !addText(String.valueOf(text.charAt(0)), true)) {
        return false;
      }

      String tailText = (!text.isEmpty() && (text.endsWith(" ") || text.endsWith("\t"))) ? text.substring(text.length() - 1) : null;

      text = text.trim();
      if (myInsidePre && text.contains("\n")) {
        boolean addLf = false;
        for (String s : text.split("\n")) {
          if (addLf) {
            if (!newLine()) {
              return false;
            }
          }
          else {
            addLf = true;
          }
          if (!onText(s)) return false;
        }
        return true;
      }

      for (String s : text.split(" ")) {
        s = s.trim();
        if (s.length() <= 0) {
          continue;
        }

        if (myScheduleNewLine && canBreakBeforeText(s)) {
          if (!newLine()) return false;
          addSpace = false;
        }

        if (addSpace) {
          if (!addText(" ", true)) return false;
        }
        else {
          addSpace = true;
        }

        if (!addText(s, true)) return false;
      }
      return !(tailText != null && !addText(tailText, true));
    }

    private static boolean canBreakBeforeText(@NotNull String text) {
      return !text.startsWith("&gt;") && !text.startsWith(",");
    }

    private boolean newLine() {
      if (onLastRow()) {
        if (myQName != null) {
          myContext.buffer.append(String.format(" <a href='psi_element://%s'>...</a>", myQName));
        }
        return false;
      }
      addText("<br/>", false);
      myCurrentColumn = 0;
      myScheduleNewLine = false;
      myCurrentRow++;
      return true;
    }

    private boolean addText(@NotNull String text, boolean countColumns) {
      if (!myDocStarted) {
        myContext.buffer.append("<br/>");
        myDocStarted = true;
      }

      if (!countColumns) {
        myContext.buffer.append(text);
        return true;
      }

      int remainingColumns = myColumnsPerRow - myCurrentColumn;
      if (onLastRow()) {
        remainingColumns -= " ...".length();
      }
      if (remainingColumns < text.length() && !newLine()) return false;

      myContext.buffer.append(text);
      myCurrentColumn += text.length();
      return true;
    }

    private boolean onLastRow() {
      return myCurrentRow >= myContext.rows - 1;
    }
  }
}
