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

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Provides utility methods for building documentation preview.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 7/10/12 8:06 AM
 */
public class DocPreviewUtil {

  private static final Set<String> TAGS_TO_ADD_LF = new HashSet<String>(Arrays.asList("p", "blockquote", "pre"));
  private static final Set<String> TAGS_TO_IGNORE = new HashSet<String>(Arrays.asList("style", "b", "small"));

  private DocPreviewUtil() {
  }

  /**
   * Allows to build a documentation preview from the given arguments. Basically, takes given 'full documentation', wraps it according
   * to the given 'desired rows and columns per-row' arguments and returns a result.
   * 
   * @param header                     target documentation header. Is expected to be a result of the
   *                                   {@link DocumentationProvider#getQuickNavigateInfo(PsiElement, PsiElement)} call
   * @param qName                      there is a possible case that not all documentation text will be included to the preview
   *                                   (according to the given 'desired rows and columns per-row' arguments). A link that points to the
   *                                   element with the given qualified name is added to the preview's end if the qName is provided then
   * @param fullText                   full documentation text (if available)
   * @param desiredRowsNumber          maximum number of rows to use at the preview's body ('header' text is not count here)
   * @param desiredSymbolsInRowNumber  desired max number of columns per row
   * @return                           preview text to use for the given arguments
   */
  @NotNull
  public static String buildPreview(@NotNull String header,
                                    @Nullable String qName,
                                    @Nullable String fullText,
                                    int desiredRowsNumber,
                                    int desiredSymbolsInRowNumber)
  {
    if (fullText == null) {
      return header;
    }

    // The algorithm is:
    //   1. Prepare header to use;
    //   2. Process full text body as follows:
    //     2.1. Count non-markup symbols until desired row symbols number is exceeded;
    //     2.2. Insert <br> after that to start a new row;
    //     2.3. Stop processing as soon as the desired rows number is reached or the text is finished;
    //   3. Add closing tags for all non-matched open tags;

    final Context context = new Context(desiredRowsNumber, desiredSymbolsInRowNumber);
    int startParseOffset = 0;

    //region 1. Prepare header to use

    // Fill in HTML header.
    startParseOffset = process(fullText, startParseOffset, fullText.length(), getHeadParser(context));
    startParseOffset += "<body>".length();
    
    //  Include information about the library/module location.
    int bracket = header.indexOf(']');
    int lf = header.indexOf('\n');
    if (bracket > 0 && (lf < 0 || bracket < lf)) {
      context.buffer.append(header.substring(0, bracket + 1)).append(" ");
    }
    
    // Include information that is available at the given header (it's not count to the given rows/columns arguments).
    startParseOffset = process(fullText, startParseOffset, fullText.length(), getHeaderParser(context, header));
    
    //endregion

    //region Parse body
    startParseOffset = process(fullText, startParseOffset, fullText.length(), getBodyParser(context));
    //endregion

    if (qName != null && startParseOffset < fullText.length()) {
      context.buffer.append(String.format("<a href='psi_element://%s'>&lt;more&gt;</a>", qName));
    }
    
    //region Add closing tags
    while (!context.openTags.isEmpty()) {
      context.buffer.append("</").append(context.openTags.pop()).append('>');
    }
    //endregion

    return context.buffer.toString();
  }
  
  @SuppressWarnings("AssignmentToForLoopParameter")
  private static int process(@NotNull String text, int start, int end, @NotNull Callback callback) {
    State state = State.TEXT;
    int dataStartOffset = start;
    int tagNameStartOffset = start;
    String tagName = null;
    for (; start < end; start++) {
      char c = text.charAt(start);
      switch (state) {
        case TEXT:
          if (c == '<') {
            if (start > dataStartOffset) {
              if (!callback.onText(text.substring(dataStartOffset, start).replace("&nbsp;", " "))) {
                return dataStartOffset;
              }
            }
            dataStartOffset = start;
            if (start < text.length() - 1 && text.charAt(start + 1) == '/') {
              state = State.INSIDE_CLOSE_TAG;
              tagNameStartOffset = ++start + 1;
            }
            else {
              state = State.INSIDE_OPEN_TAG;
              tagNameStartOffset = start + 1;
            }
          }
          break;
        case INSIDE_OPEN_TAG:
          if (c == ' ') {
            tagName = text.substring(tagNameStartOffset, start);
          }
          else if (c == '/') {
            if (start < text.length() - 1 && text.charAt(start + 1) == '>') {
              if (tagName == null) {
                tagName = text.substring(tagNameStartOffset, start);
              }
              if (!callback.onStandaloneTag(tagName, text.substring(dataStartOffset, start + 2))) {
                return dataStartOffset;
              }
              tagName = null;
              state = State.TEXT;
              dataStartOffset = ++start + 1;
              break;
            }
          }
          else if (c == '>') {
            if (tagName == null) {
              tagName = text.substring(tagNameStartOffset, start);
            }
            if (!callback.onOpenTag(tagName, text.substring(dataStartOffset, start + 1))) {
              return dataStartOffset;
            }
            tagName = null;
            state = State.TEXT;
            dataStartOffset = start + 1;
          }
          break;
        case INSIDE_CLOSE_TAG:
          if (c == '>') {
            if (tagName == null) {
              tagName = text.substring(tagNameStartOffset, start);
            }
            if (!callback.onCloseTag(tagName, text.substring(dataStartOffset, start + 1))) {
              return dataStartOffset;
            }
            tagName = null;
            state = State.TEXT;
            dataStartOffset = start + 1;
          }
      }
    }
    return start;
  }
  
  @NotNull
  private static Callback getHeadParser(@NotNull Context context) {
    return new AbstractCallback(context, false) {
      @Override
      public boolean onOpenTag(@NotNull String name, @NotNull String text) {
        super.onOpenTag(name, text);
        return !"body".equals(name);
      }
    };
  }
  
  @NotNull
  private static Callback getHeaderParser(@NotNull Context context, @NotNull final String header) {
    return new AbstractCallback(context, false) {
      
      private boolean myStop;
      
      @Override
      public boolean onOpenTag(@NotNull String name, @NotNull String text) {
        return !myStop && super.onOpenTag(name, text);
      }

      @Override
      public boolean onText(@NotNull String text) {
        boolean addLf = false;
        for (String s : text.split("\n")) {
          if (addLf) {
            newLine();
          }
          else {
            addLf = true;
          }
          
          if (s.length() <= 0) {
            continue;
          }

          if (!header.contains(s) && s.startsWith("java.lang.")) {
            s = s.substring("java.lang.".length());
          }

          if (myStop || !header.contains(s)) {
            return false;
          }

          if (header.endsWith(s)) {
            myStop = true;
          }

          addText(s);
        }
        if (text.endsWith("\n")) {
          newLine();
        }
        return true;
      }

      @Override
      protected boolean canBreakBeforeText(@NotNull String text) {
        // Don't allow line break before the closing type parameter bracket.
        return !text.startsWith("&gt;") && !text.startsWith(",");
      }
    };
  }
  
  @NotNull
  private static Callback getBodyParser(@NotNull final Context context) {
    return new AbstractCallback(context, true) {
      
      @Override
      public boolean onText(@NotNull String text) {
        return addText(text);
      }
    };
  }
  
  private static class Context {
    
    @NotNull public final Stack<String> openTags = new Stack<String>();
    @NotNull public final StringBuilder buffer   = new StringBuilder();
    public final int rows;
    public final int columnsPerRow;
    public int currentRow;
    public int currentColumn;

    public Context(int rows, int columnsPerRow) {
      this.rows = rows;
      this.columnsPerRow = columnsPerRow;
    }
  }

  private interface Callback {
    boolean onOpenTag(@NotNull String name, @NotNull String text);

    boolean onCloseTag(@NotNull String name, @NotNull String text);

    boolean onStandaloneTag(@NotNull String name, @NotNull String text);

    boolean onText(@NotNull String text);
  }
  
  private static abstract class AbstractCallback implements Callback {

    @NotNull protected final Context myContext;
    private final boolean myCountRows;
    private       boolean myScheduleNewLine;
    private boolean myInsidePre;

    protected AbstractCallback(@NotNull Context context, boolean countRows) {
      myContext = context;
      myCountRows = countRows;
    }

    @Override
    public boolean onOpenTag(@NotNull String name, @NotNull String text) {
      if ("pre".equals(name)) {
        myInsidePre = true;
      }
      if (!processDelayedLfTag(name)) {
        return myContext.currentRow < myContext.rows;
      }

      if (!TAGS_TO_IGNORE.contains(name)) {
        myContext.buffer.append(text);
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

    @Override
    public boolean onCloseTag(@NotNull String name, @NotNull String text) {
      if ("pre".equals(name)) {
        myInsidePre = false;
      }

      if (!processDelayedLfTag(name)) {
        return myContext.currentRow < myContext.rows;
      }
      
      if (!TAGS_TO_IGNORE.contains(name)) {
        myContext.buffer.append(text);
        myContext.openTags.remove(name);
      }
      return true;
    }

    @Override
    public boolean onStandaloneTag(@NotNull String name, @NotNull String text) {
      if (!processDelayedLfTag(name)) {
        return true;
      }

      if (!TAGS_TO_IGNORE.contains(name)) {
        myContext.buffer.append(text);
      }
      return true;
    }

    @Override
    public boolean onText(@NotNull String text) {
      myContext.buffer.append(text);
      return true;
    }

    protected boolean canBreakBeforeText(@NotNull String text) {
      return true;
    }

    protected boolean addText(@NotNull String text) {
      boolean addSpace = false;
      if (!text.isEmpty() && (text.startsWith(" ") || text.startsWith("\t"))) {
        myContext.buffer.append(text.charAt(0));
        myContext.currentColumn++;
      }

      String tailText = (!text.isEmpty() && (text.endsWith(" ") || text.endsWith("\t"))) ? text.substring(text.length() - 1) : null; 

      text = text.trim();
      if (myInsidePre && text.contains("\n")) {
        boolean addLf = false;
        for (String s : text.split("\n")) {
          if (addLf) {
            newLine();
            if (myContext.currentRow >= myContext.rows) {
              return false;
            }
          }
          else {
            addLf = true;
          }
          addText(s);
          if (myContext.currentRow >= myContext.rows) {
            return false;
          }
        }
        return myContext.currentRow < myContext.rows;
      }
      
      for (String s : text.split(" ")) {
        s = s.trim();
        if (s.length() <= 0) {
          continue;
        }
        
        if (myScheduleNewLine && canBreakBeforeText(s)) {
          newLine();
          addSpace = false;
          if (myContext.currentRow >= myContext.rows) {
            return false;
          }
        }
        
        if (addSpace) {
          myContext.buffer.append(" ");
          myContext.currentColumn++;
        }
        else {
          addSpace = true;
        }
        
        myContext.currentColumn += s.length();
        myContext.buffer.append(s);
        if (myContext.currentColumn < myContext.columnsPerRow) {
          continue;
        }
        myScheduleNewLine = true;
      }
      if (tailText != null) {
        myContext.buffer.append(tailText);
        myContext.currentColumn += tailText.length();
      }
      return true;
    }

    protected void newLine() {
      myContext.buffer.append("<br/>");
      myContext.currentColumn = 0;
      myScheduleNewLine = false;
      if (myCountRows) {
        myContext.currentRow++;
      }
    }
  }

  private enum State {TEXT, INSIDE_OPEN_TAG, INSIDE_CLOSE_TAG}
}
