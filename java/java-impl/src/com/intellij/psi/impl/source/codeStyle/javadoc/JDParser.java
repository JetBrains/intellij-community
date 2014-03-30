/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.codeStyle.javadoc;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Javadoc parser
 *
 * @author Dmitry Skavish
 */
public class JDParser {

  private static final String PRE_TAG_START = "<pre>";
  private static final String PRE_TAG_END = "</pre>";
  private static final String P_END_TAG = "</p>";
  private static final String P_START_TAG = "<p>";
  private static final String SELF_CLOSED_P_TAG = "<p/>";

  private final CodeStyleSettings mySettings;
  private final LanguageLevel myLanguageLevel;

  public JDParser(@NotNull CodeStyleSettings settings, @NotNull LanguageLevel languageLevel) {
    mySettings = settings;
    myLanguageLevel = languageLevel;
  }

  private static final char lineSeparator = '\n';

  @NotNull
  public JDComment parse(@Nullable String text, @NotNull JDComment comment) {
    if (text == null) return comment;

    List<Boolean> markers = new ArrayList<Boolean>();
    List<String> l = toArray(text, "\n", markers);

    //if it is - we are dealing with multiline comment:
    // /**
    //  * comment
    //  */
    //which shouldn't be wrapped into one line comment like /** comment */
    if (text.indexOf('\n') >= 0) {
      comment.setMultiLine(true);
    }

    if (l == null) return comment;
    int size = l.size();
    if (size == 0) return comment;

    // preprocess strings - removes first '*'
    for (int i = 0; i < size; i++) {
      String line = l.get(i);
      line = line.trim();
      if (!line.isEmpty()) {
        if (line.charAt(0) == '*') {
          if ((markers.get(i)).booleanValue()) {
            if (line.length() > 1 && line.charAt(1) == ' ') {
              line = line.substring(2);
            }
            else {
              line = line.substring(1);
            }
          }
          else {
            line = line.substring(1).trim();
          }
        }
      }
      l.set(i, line);
    }

    StringBuilder sb = new StringBuilder();
    String tag = null;
    for (int i = 0; i <= size; i++) {
      String line = i == size ? null : l.get(i);
      if (i == size || !line.isEmpty()) {
        if (i == size || line.charAt(0) == '@') {
          if (tag == null) {
            comment.setDescription(sb.toString());
          }
          else {
            int j = 0;
            String myline = sb.toString();
            for (; j < tagParsers.length; j++) {
              TagParser parser = tagParsers[j];
              if (parser.parse(tag, myline, comment)) break;
            }
            if (j == tagParsers.length) {
              comment.addUnknownTag("@" + tag + " " + myline);
            }
          }

          if (i < size) {
            int last_idx = line.indexOf(' ');
            if (last_idx == -1) {
              tag = line.substring(1);
              line = "";
            }
            else {
              tag = line.substring(1, last_idx);
              line = line.substring(last_idx).trim();
            }
            sb.setLength(0);
            sb.append(line);
          }
        }
        else {
          if (sb.length() > 0) {
            sb.append(lineSeparator);
          }
          sb.append(line);
        }
      }
      else {
        if (sb.length() > 0) {
          sb.append(lineSeparator);
        }
      }
    }

    return comment;
  }

  /**
   * Breaks the specified string by the specified separators into array of strings
   *
   * @param s          the specified string
   * @param separators the specified separators
   * @param markers    if this parameter is not null then it will be filled with Boolean values:
   *                   true if the corresponding line in returned list is inside &lt;pre&gt; tag,
   *                   false if it is outside
   * @return array of strings (lines)
   */
  @Nullable
  private List<String> toArray(@Nullable String s, @NotNull String separators, @Nullable List<Boolean> markers) {
    if (s == null) return null;
    s = s.trim();
    if (s.isEmpty()) return null;
    boolean p2nl = markers != null && mySettings.JD_P_AT_EMPTY_LINES;
    List<String> list = new ArrayList<String>();
    StringTokenizer st = new StringTokenizer(s, separators, true);
    boolean first = true;
    int preCount = 0;
    int curPos = 0;
    while (st.hasMoreTokens()) {
      String token = st.nextToken();
      curPos += token.length();

      if (separators.contains(token)) {
        if (!first) {
          list.add("");
          if (markers != null) markers.add(Boolean.valueOf(preCount > 0));
        }
        first = false;
      }
      else {
        first = true;
        if (p2nl) {
          if (isParaTag(token) && s.indexOf(P_END_TAG, curPos) < 0) {
            list.add("");
            markers.add(Boolean.valueOf(preCount > 0));
            continue;
          }
        }
        if (preCount == 0) token = token.trim();

        list.add(token);

        if (markers != null) {
          if (token.contains(PRE_TAG_START)) preCount++;
          markers.add(Boolean.valueOf(preCount > 0));
          if (token.contains(PRE_TAG_END)) preCount--;
        }

      }
    }
    return list;
  }

  private static boolean isParaTag(@NotNull final String token) {
    String withoutWS = removeWhiteSpacesFrom(token).toLowerCase();
    return withoutWS.equals(SELF_CLOSED_P_TAG) || withoutWS.equals(P_START_TAG);
  }

  @NotNull
  private static String removeWhiteSpacesFrom(@NotNull final String token) {
    final StringBuilder result = new StringBuilder();
    for (char c : token.toCharArray()) {
      if (c != ' ') result.append(c);
    }
    return result.toString();
  }

  /**
   * Processes all lines (char sequences separated by line feed symbol) from the given string slitting them if necessary
   * ensuring that every returned line contains less symbols than the given width.
   *
   * @param s     the specified string
   * @param width width of the wrapped text
   * @return array of strings (lines)
   */
  @Nullable
  private List<String> toArrayWrapping(@Nullable String s, int width) {
    List<String> list = new ArrayList<String>();
    List<Pair<String, Boolean>> pairs = splitToParagraphs(s);
    if (pairs == null) {
      return null;
    }
    for (Pair<String, Boolean> pair : pairs) {
      String seq = pair.getFirst();
      boolean isMarked = pair.getSecond();

      if (seq.isEmpty()) {
        // keep empty lines
        list.add("");
        continue;
      }
      while (true) {
        if (seq.length() < width) {
          // keep remaining line and proceed with next paragraph
          seq = isMarked ? seq : seq.trim();
          list.add(seq);
          break;
        }
        else {
          // wrap paragraph

          int wrapPos = Math.min(seq.length() - 1, width);
          wrapPos = seq.lastIndexOf(' ', wrapPos);

          // either the only word is too long or it looks better to wrap
          // after the border
          if (wrapPos <= 2 * width / 3) {
            wrapPos = Math.min(seq.length() - 1, width);
            wrapPos = seq.indexOf(' ', wrapPos);
          }

          // wrap now
          if (wrapPos >= seq.length() - 1 || wrapPos < 0) {
            seq = isMarked ? seq : seq.trim();
            list.add(seq);
            break;
          }
          else {
            list.add(seq.substring(0, wrapPos));
            seq = seq.substring(wrapPos + 1);
          }
        }
      }
    }

    return list;
  }

  /**
   * Processes given string and produces on its basis set of pairs like <code>'(string; flag)'</code> where <code>'string'</code>
   * is interested line and <code>'flag'</code> indicates if it is wrapped to {@code <pre>} tag.
   *
   * @param s   string to process
   * @return    processing result
   */
  @Nullable
  private List<Pair<String, Boolean>> splitToParagraphs(@Nullable String s) {
    if (s == null) return null;
    s = s.trim();
    if (s.isEmpty()) return null;

    List<Pair<String, Boolean>> result = new ArrayList<Pair<String, Boolean>>();

    StringBuilder sb = new StringBuilder();
    List<Boolean> markers = new ArrayList<Boolean>();
    List<String> list = toArray(s, "\n", markers);
    Boolean[] marks = markers.toArray(new Boolean[markers.size()]);
    markers.clear();
    assert list != null;
    for (int i = 0; i < list.size(); i++) {
      String s1 = list.get(i);
      if (marks[i].booleanValue()) {
        if (sb.length() != 0) {
          result.add(new Pair<String, Boolean>(sb.toString(), false));
          sb.setLength(0);
        }
        result.add(new Pair<String, Boolean>(s1, marks[i]));
      }
      else {
        if (s1.isEmpty()) {
          if (sb.length() != 0) {
            result.add(new Pair<String, Boolean>(sb.toString(), false));
            sb.setLength(0);
          }
          result.add(new Pair<String, Boolean>("", marks[i]));
        }
        else if (mySettings.JD_PRESERVE_LINE_FEEDS) {
          result.add(new Pair<String, Boolean>(s1, marks[i]));
        }
        else {
          if (sb.length() != 0) sb.append(' ');
          sb.append(s1);
        }
      }
    }
    if (!mySettings.JD_PRESERVE_LINE_FEEDS && sb.length() != 0) {
      result.add(new Pair<String, Boolean>(sb.toString(), false));
    }
    return result;
  }

  abstract static class TagParser {

    abstract boolean parse(String tag, String line, JDComment c);
  }

  private static final TagParser[] tagParsers = {
    new TagParser() {
      @Override
      boolean parse(String tag, String line, JDComment c) {
        boolean isMyTag = JDTag.SEE.tagEqual(tag);
        if (isMyTag) {
          c.addSeeAlso(line);
        }
        return isMyTag;
      }
    },
    new TagParser() {
      @Override
      boolean parse(String tag, String line, JDComment c) {
        boolean isMyTag = JDTag.SINCE.tagEqual(tag);
        if (isMyTag) {
          c.setSince(line);
        }
        return isMyTag;
      }
    },
    new TagParser() {
      @Override
      boolean parse(String tag, String line, JDComment c) {
        boolean isMyTag = c instanceof JDClassComment && JDTag.VERSION.tagEqual(tag);
        if (isMyTag) {
          ((JDClassComment)c).setVersion(line);
        }
        return isMyTag;
      }
    },
    new TagParser() {
      @Override
      boolean parse(String tag, String line, JDComment c) {
        boolean isMyTag = JDTag.DEPRECATED.tagEqual(tag);
        if (isMyTag) {
          c.setDeprecated(line);
        }
        return isMyTag;
      }
    },
    new TagParser() {
      @Override
      boolean parse(String tag, String line, JDComment c) {
        boolean isMyTag = c instanceof JDMethodComment && JDTag.RETURN.tagEqual(tag);
        if (isMyTag) {
          JDMethodComment mc = (JDMethodComment)c;
          mc.setReturnTag(line);
        }
        return isMyTag;
      }
    },
    new TagParser() {
      @Override
      boolean parse(String tag, String line, JDComment c) {
        boolean isMyTag = c instanceof JDParamListOwnerComment && JDTag.PARAM.tagEqual(tag);
        if (isMyTag) {
          JDParamListOwnerComment mc = (JDParamListOwnerComment)c;
          int idx;
          for (idx = 0; idx < line.length(); idx++) {
            char ch = line.charAt(idx);
            if (Character.isWhitespace(ch)) break;
          }
          if (idx == line.length()) {
            mc.addParameter(line, "");
          }
          else {
            String name = line.substring(0, idx);
            String desc = line.substring(idx).trim();
            mc.addParameter(name, desc);
          }
        }
        return isMyTag;
      }
    },
    new TagParser() {
      @Override
      boolean parse(String tag, String line, JDComment c) {
        boolean isMyTag = c instanceof JDMethodComment && (JDTag.THROWS.tagEqual(tag) || JDTag.EXCEPTION.tagEqual(tag));
        if (isMyTag) {
          JDMethodComment mc = (JDMethodComment)c;
          int idx;
          for (idx = 0; idx < line.length(); idx++) {
            char ch = line.charAt(idx);
            if (Character.isWhitespace(ch)) break;
          }
          if (idx == line.length()) {
            mc.addThrow(line, "");
          }
          else {
            String name = line.substring(0, idx);
            String desc = line.substring(idx).trim();
            mc.addThrow(name, desc);
          }
        }
        return isMyTag;
      }
    },
    new TagParser() {
      @Override
      boolean parse(String tag, String line, JDComment c) {
        boolean isMyTag = c instanceof JDClassComment && JDTag.AUTHOR.tagEqual(tag);
        if (isMyTag) {
          JDClassComment cl = (JDClassComment)c;
          cl.addAuthor(line.trim());
        }
        return isMyTag;
      }
    },
  };

  /**
   * @see JDParser#formatJDTagDescription(String, CharSequence, boolean, int)
   */
  @NotNull
  protected StringBuilder formatJDTagDescription(@Nullable String s, @NotNull CharSequence prefix) {
    return formatJDTagDescription(s, prefix, false, 0);
  }

  private static boolean lineHasUnclosedPreTag(@NotNull String line) {
    return StringUtil.getOccurrenceCount(line, PRE_TAG_START) > StringUtil.getOccurrenceCount(line, PRE_TAG_END);
  }

  /**
   * Returns formatted JavaDoc tag description, according to selected configuration
   * @param str JavaDoc tag description
   * @param prefix JavaDoc prefix(like "      *  ") which will be appended to every new line
   * @param firstLineShorter flag if first line should be shorter (has another prefix length than other lines)
   * @param firstLinePrefixLength first line prefix length
   * @return formatted JavaDoc tag description
   */
  @NotNull
  protected StringBuilder formatJDTagDescription(@Nullable String str,
                                                 @NotNull CharSequence prefix,
                                                 boolean firstLineShorter,
                                                 int firstLinePrefixLength)
  {
    StringBuilder sb = new StringBuilder();
    List<String> list;

    //If wrap comments selected, comments should be wrapped by the right margin
    if (mySettings.WRAP_COMMENTS) {
      list = toArrayWrapping(str, mySettings.RIGHT_MARGIN - prefix.length());

      if (firstLineShorter
          && list != null && !list.isEmpty()
          && list.get(0).length() > mySettings.RIGHT_MARGIN - firstLinePrefixLength)
      {
        list = new ArrayList<String>();
        //want the first line to be shorter, according to it's prefix
        String firstLine = toArrayWrapping(str, mySettings.RIGHT_MARGIN - firstLinePrefixLength).get(0);
        //so now first line is exactly same width we need
        list.add(firstLine);
        str = str.substring(firstLine.length());
        //actually there is one more problem - when first line has unclosed <pre> tag, substring should be processed if it's inside <pre>
        boolean unclosedPreTag = lineHasUnclosedPreTag(firstLine);
        if (unclosedPreTag) {
          str = PRE_TAG_START + str.replaceAll("^\\s+", "");
        }

        //getting all another lines according to their prefix
        List<String> subList = toArrayWrapping(str, mySettings.RIGHT_MARGIN - prefix.length());

        //removing pre tag
        if (unclosedPreTag && subList != null && !subList.isEmpty()) {
          String firstLineTagRemoved = subList.get(0).substring(PRE_TAG_START.length());
          subList.set(0, firstLineTagRemoved);
        }
        if (subList != null) list.addAll(subList);
      }
    }
    else {
      list = toArray(str, "\n", new ArrayList<Boolean>());
    }

    if (list == null) {
      sb.append('\n');
    }
    else {
      boolean insidePreTag = false;
      for (int i = 0; i < list.size(); i++) {
        String line = list.get(i);
        if (line.isEmpty() && !mySettings.JD_KEEP_EMPTY_LINES) continue;
        if (i != 0) sb.append(prefix);
        if (line.isEmpty() && mySettings.JD_P_AT_EMPTY_LINES && !insidePreTag) {
          if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
            //Self-closing elements are not allowed for javadoc tool from JDK8
            sb.append(P_START_TAG);
          }
          else {
            sb.append(SELF_CLOSED_P_TAG);
          }
        }
        else {
          sb.append(line);

          // We want to track if we're inside <pre>...</pre> in order to not generate <p/> there.
          if (PRE_TAG_START.equals(line)) {
            insidePreTag = true;
          }
          else if (PRE_TAG_END.equals(line)) {
            insidePreTag = false;
          }
        }
        sb.append('\n');
      }
    }

    return sb;
  }
}
