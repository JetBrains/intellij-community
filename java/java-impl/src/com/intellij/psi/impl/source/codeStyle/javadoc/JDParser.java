/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NonNls;
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
  
  private final CodeStyleSettings mySettings;

  public JDParser(CodeStyleSettings settings) {
    mySettings = settings;
  }

  private static final char lineSeparator = '\n';

  public JDComment parse(String text, JDComment c) {
    if (text == null) return c;

    ArrayList<Boolean> markers = new ArrayList<Boolean>();
    ArrayList<String> l = toArray(text, "\n", markers);
    if (l == null) return c;
    int size = l.size();
    if (size == 0) return c;

    // preprocess strings - removes first '*'
    for (int i = 0; i < size; i++) {
      String line = l.get(i);
      line = line.trim();
      if (line.length() > 0) {
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

    StringBuffer sb = new StringBuffer();
    String tag = null;
    for (int i = 0; i <= size; i++) {
      String line = i == size ? null : l.get(i);
      if (i == size || line.length() > 0) {
        if (i == size || line.charAt(0) == '@') {
          if (tag == null) {
            c.setDescription(sb.toString());
          }
          else {
            int j = 0;
            String myline = sb.toString();
            for (; j < tagParsers.length; j++) {
              TagParser parser = tagParsers[j];
              if (parser.parse(tag, myline, c)) break;
            }
            if (j == tagParsers.length) {
              c.addUnknownTag("@" + tag + " " + myline);
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

    return c;
  }

  /**
   * Breaks the specified string by lineseparator into array of strings
   * 
   * @param s the specified string
   * @return array of strings (lines)
   */
  public ArrayList<String> toArrayByNL(String s) {
    return toArray(s, "\n", null);
  }

  /**
   * Breaks the specified string by comma into array of strings
   * 
   * @param s the specified string
   * @return list of strings
   */
  public ArrayList<String> toArrayByComma(String s) {
    return toArray(s, ",", null);
  }

  /**
   * Breaks the specified string by the specified separators into array of strings
   * 
   * @param s          the specified string
   * @param separators the specified separators
   * @param markers    if this parameter is not null then it will be filled with Boolean values:
   *                   true if the correspoding line in returned list is inside &lt;pre&gt; tag,
   *                   false if it is outside
   * @return array of strings (lines)
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  private ArrayList<String> toArray(String s, String separators, ArrayList<Boolean> markers) {
    if (s == null) return null;
    s = s.trim();
    if (s.length() == 0) return null;
    boolean p2nl = markers != null && mySettings.JD_P_AT_EMPTY_LINES;
    ArrayList<String> list = new ArrayList<String>();
    StringTokenizer st = new StringTokenizer(s, separators, true);
    boolean first = true;
    int preCount = 0;
    int curPos = 0;
    while (st.hasMoreTokens()) {
      String token = st.nextToken();
      curPos += token.length();

      if (separators.indexOf(token) >= 0) {
        if (!first) {
          list.add("");
          if (markers != null) markers.add(Boolean.valueOf(preCount > 0));
        }
        first = false;
      }
      else {
        first = true;
        if (p2nl) {
          if (isParaTag(token) && s.indexOf("</p>", curPos) < 0) {
            list.add("");
            markers.add(Boolean.valueOf(preCount > 0));
            continue;
          }
        }
        if (preCount == 0) token = token.trim();

        list.add(token);

        if (markers != null) {
          if (token.indexOf("<pre>") >= 0) preCount++;
          markers.add(Boolean.valueOf(preCount > 0));
          if (token.indexOf("</pre>") >= 0) preCount--;
        }

      }
    }
    return list;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private boolean isParaTag(final String token) {
    String withoutWS = removeWhiteSpacesFrom(token).toLowerCase();
    return withoutWS.equals("<p/>") || withoutWS.equals("<p>");
  }

  private String removeWhiteSpacesFrom(final String token) {
    final StringBuffer result = new StringBuffer();
    for (char c : token.toCharArray()) {
      if (c != ' ') result.append(c);
    }
    return result.toString();
  }

  public static String toLines(ArrayList l) {
    if (l == null || l.size() == 0) return null;
    StringBuffer sb = new StringBuffer();
    for (Object aL : l) {
      String s = (String)aL;
      if (sb.length() > 0) {
        sb.append(lineSeparator);
      }
      sb.append(s);
    }
    return sb.toString();
  }

  public static String toCommaSeparated(ArrayList<String> l) {
    if (l == null || l.size() == 0) return null;
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < l.size(); i++) {
      String s = l.get(i);
      if (i != 0) {
        sb.append(", ");
      }
      sb.append(s);
    }
    return sb.toString();
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
  private List<String> toArrayWrapping(String s, int width) {
    List<String> list = new ArrayList<String>();
    List<Pair<String, Boolean>> pairs = splitToParagraphs(s);
    if (pairs == null) {
      return null;
    }
    for (Pair<String, Boolean> pair : pairs) {
      String seq = pair.getFirst();
      boolean isMarked = pair.getSecond();

      if (seq.length() == 0) {
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
    if (s == null /* just to make inspection happy*/ || s.isEmpty()) return null;

    List<Pair<String, Boolean>> result = new ArrayList<Pair<String, Boolean>>();
    
    StringBuilder sb = new StringBuilder();
    ArrayList<Boolean> markers = new ArrayList<Boolean>();
    ArrayList<String> list = toArray(s, "\n", markers);
    Boolean[] marks = markers.toArray(new Boolean[markers.size()]);
    markers.clear();
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
  
  static abstract class TagParser {

    abstract boolean parse(String tag, String line, JDComment c);
  }

  private static final @NonNls String SEE_TAG = "see";
  private static final @NonNls String SINCE_TAG = "since";
  private static final @NonNls String VERSION_TAG = "version";
  private static final @NonNls String DEPRECATED_TAG = "deprecated";
  private static final @NonNls String RETURN_TAG = "return";
  private static final @NonNls String PARAM_TAG = "param";
  private static final @NonNls String THROWS_TAG = "throws";
  private static final @NonNls String EXCEPTION_TAG = "exception";
  private static final @NonNls String AUTHOR_TAG = "author";

  private static final TagParser[] tagParsers = {
    new TagParser() {
      boolean parse(String tag, String line, JDComment c) {
        boolean isMyTag = SEE_TAG.equals(tag);
        if (isMyTag) {
          c.addSeeAlso(line);
        }
        return isMyTag;
      }
    },
    new TagParser() {
      boolean parse(String tag, String line, JDComment c) {
        boolean isMyTag = SINCE_TAG.equals(tag);
        if (isMyTag) {
          c.setSince(line);
        }
        return isMyTag;
      }
    },
    new TagParser() {
      boolean parse(String tag, String line, JDComment c) {
        boolean isMyTag = c instanceof JDClassComment && VERSION_TAG.equals(tag);
        if (isMyTag) {
          ((JDClassComment)c).setVersion(line);
        }
        return isMyTag;
      }
    },
    new TagParser() {
      boolean parse(String tag, String line, JDComment c) {
        boolean isMyTag = DEPRECATED_TAG.equals(tag);
        if (isMyTag) {
          c.setDeprecated(line);
        }
        return isMyTag;
      }
    },
    new TagParser() {
      boolean parse(String tag, String line, JDComment c) {
        boolean isMyTag = c instanceof JDMethodComment && RETURN_TAG.equals(tag);
        if (isMyTag) {
          JDMethodComment mc = (JDMethodComment)c;
          mc.setReturnTag(line);
        }
        return isMyTag;
      }
    },
    new TagParser() {
      boolean parse(String tag, String line, JDComment c) {
        boolean isMyTag = c instanceof JDParamListOwnerComment && PARAM_TAG.equals(tag);
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
      boolean parse(String tag, String line, JDComment c) {
        boolean isMyTag = c instanceof JDMethodComment && (THROWS_TAG.equals(tag) || EXCEPTION_TAG.equals(tag));
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
      boolean parse(String tag, String line, JDComment c) {
        boolean isMyTag = c instanceof JDClassComment && AUTHOR_TAG.equals(tag);
        if (isMyTag) {
          JDClassComment cl = (JDClassComment)c;
          cl.addAuthor(line.trim());
        }
        return isMyTag;
      }
    },
/*        new TagParser() {
            boolean parse( String tag, String line, JDComment c ) {
                XDTag xdtag = XDTag.parse(tag, line);
                if( xdtag != null ) {
                    c.addXDocTag(xdtag);
                }
                return xdtag != null;
            }
        },*/
  };

  protected StringBuffer splitIntoCLines(String s, String prefix) {
    return splitIntoCLines(s, prefix, true);
  }

  protected StringBuffer splitIntoCLines(String s, String prefix, boolean add_prefix_to_first_line) {
    return splitIntoCLines(s, new StringBuffer(prefix), add_prefix_to_first_line);
  }

  protected StringBuffer splitIntoCLines(String s, StringBuffer prefix, boolean add_prefix_to_first_line) {
    @NonNls StringBuffer sb = new StringBuffer();
    if (add_prefix_to_first_line) {
      sb.append(prefix);
    }
    List<String> list = mySettings.WRAP_COMMENTS
                             ? toArrayWrapping(s, mySettings.RIGHT_MARGIN - prefix.length())
                             : toArray(s, "\n", new ArrayList<Boolean>());
    if (list == null) {
      sb.append('\n');
    }
    else {
      boolean insidePreTag = false;
      for (int i = 0; i < list.size(); i++) {
        String line = list.get(i);
        if (line.length() == 0 && !mySettings.JD_KEEP_EMPTY_LINES) continue;
        if (i != 0) sb.append(prefix);
        if (line.length() == 0 && mySettings.JD_P_AT_EMPTY_LINES && !insidePreTag) {
          sb.append("<p/>");
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
