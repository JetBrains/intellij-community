// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.codeStyle.javadoc;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

/**
 * Javadoc parser
 *
 * @author Dmitry Skavish
 */
public class JDParser {
  private static final String JAVADOC_HEADER = "/**";
  private static final String PRE_TAG_START = "<pre>";
  private static final String PRE_TAG_END = "</pre>";
  private static final String P_END_TAG = "</p>";
  private static final String P_START_TAG = "<p>";
  private static final String SELF_CLOSED_P_TAG = "<p/>";

  private final JavaCodeStyleSettings mySettings;
  private final CommonCodeStyleSettings myCommonSettings;

  public JDParser(@NotNull CodeStyleSettings settings) {
    mySettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    myCommonSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
  }

  public void formatCommentText(@NotNull PsiElement element, @NotNull CommentFormatter formatter) {
    CommentInfo info = getElementsCommentInfo(element);
    if (info == null || !isJavadoc(info)) return;

    JDComment comment = parse(info, formatter);
    if (comment != null) {
      String indent = formatter.getIndent(info.commentOwner);
      String commentText = comment.generate(indent);
      formatter.replaceCommentText(commentText, info.docComment);
    }
  }

  private static boolean isJavadoc(CommentInfo info) {
    return JAVADOC_HEADER.equals(info.commentHeader);
  }

  private static CommentInfo getElementsCommentInfo(@Nullable PsiElement psiElement) {
    if (psiElement instanceof PsiDocComment) {
      PsiDocComment docComment = (PsiDocComment)psiElement;

      PsiJavaDocumentedElement owner = docComment.getOwner();
      if (owner != null) {
        return getCommentInfo(docComment, owner);
      }

      PsiElement parent = docComment.getParent();
      if (parent instanceof PsiJavaFile) {
        return getCommentInfo(docComment, parent);
      }
    }
    else if (psiElement instanceof PsiJavaDocumentedElement) {
      PsiJavaDocumentedElement owner = (PsiJavaDocumentedElement)psiElement;
      PsiDocComment docComment = owner.getDocComment();
      if (docComment != null) {
        return getCommentInfo(docComment, owner);
      }
    }

    return null;
  }

  private static CommentInfo getCommentInfo(@NotNull PsiDocComment docComment, @NotNull PsiElement owner) {
    String commentHeader = null;
    String commentFooter = null;

    StringBuilder sb = new StringBuilder();
    boolean first = true;
    PsiElement e = docComment;
    while (true) {
      if (e instanceof PsiDocComment) {
        PsiComment cm = (PsiComment)e;
        String text = cm.getText();
        if (text.startsWith("//")) {
          if (!first) sb.append('\n');
          sb.append(text.substring(2).trim());
        }
        else if (text.startsWith("/*")) {
          int commentHeaderEndOffset = CharArrayUtil.shiftForward(text, 1, "*");
          int commentFooterStartOffset = CharArrayUtil.shiftBackward(text, text.length() - 2, "*");

          if (commentHeaderEndOffset <= commentFooterStartOffset) {
            commentHeader = text.substring(0, commentHeaderEndOffset);
            commentFooter = text.substring(commentFooterStartOffset + 1);
            text = text.substring(commentHeaderEndOffset, commentFooterStartOffset + 1);
          }
          else {
            commentHeader = text.substring(0, commentHeaderEndOffset);
            text = "";
            commentFooter = "";
          }
          sb.append(text);
        }
      }
      else if (!(e instanceof PsiWhiteSpace || e instanceof PsiComment)) {
        break;
      }
      first = false;
      e = e.getNextSibling();
    }

    return new CommentInfo(docComment, owner, commentHeader, sb.toString(), commentFooter);
  }

  private JDComment parse(@NotNull CommentInfo info, @NotNull CommentFormatter formatter) {
    JDComment comment = createComment(info.commentOwner, formatter);
    parse(info.comment, comment);
    if (info.commentHeader != null) {
      comment.setFirstCommentLine(info.commentHeader);
    }
    if (info.commentFooter != null) {
      comment.setLastCommentLine(info.commentFooter);
    }
    return comment;
  }

  private static JDComment createComment(@NotNull PsiElement commentOwner, @NotNull CommentFormatter formatter) {
    if (commentOwner instanceof PsiClass) {
      return new JDClassComment(formatter);
    }
    else if (commentOwner instanceof PsiMethod) {
      return new JDMethodComment(formatter);
    }
    else {
      return new JDComment(formatter);
    }
  }

  private void parse(@Nullable String text, @NotNull JDComment comment) {
    if (text == null) return;

    List<Boolean> markers = new ArrayList<>();
    List<String> l = toArray(text, markers);

    //if it is - we are dealing with multiline comment:
    // /**
    //  * comment
    //  */
    //which shouldn't be wrapped into one line comment like /** comment */
    if (text.indexOf('\n') >= 0) {
      comment.setMultiLine(true);
    }

    if (l == null) return;
    int size = l.size();
    if (size == 0) return;

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
    boolean isInsidePreTag = false;

    for (int i = 0; i <= size; i++) {
      String line = i == size ? null : l.get(i);
      if (i == size || !line.isEmpty()) {
        if (i == size || line.charAt(0) == '@' && !isInsidePreTag) {
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
            sb.append('\n');
          }
          sb.append(line);
        }
      }
      else {
        if (sb.length() > 0) {
          sb.append('\n');
        }
      }

      if (line != null) {
        isInsidePreTag = isInsidePreTag
                         ? !lineHasClosingPreTag(line)
                         : lineHasUnclosedPreTag(line);
      }
    }
  }

  /**
   * Breaks the specified string by the specified separators into array of strings
   *
   * @param s          the specified string
   * @param markers    if this parameter is not null then it will be filled with Boolean values:
   *                   true if the corresponding line in returned list is inside &lt;pre&gt; tag,
   *                   false if it is outside
   * @return array of strings (lines)
   */
  @Nullable
  private List<String> toArray(@Nullable String s, @Nullable List<Boolean> markers) {
    if (s == null) return null;
    s = s.trim();
    if (s.isEmpty()) return null;
    boolean p2nl = markers != null && mySettings.JD_P_AT_EMPTY_LINES;
    List<String> list = new ArrayList<>();
    StringTokenizer st = new StringTokenizer(s, "\n", true);
    boolean first = true;
    int preCount = 0;
    int curPos = 0;
    while (st.hasMoreTokens()) {
      String token = st.nextToken();
      curPos += token.length();

      if ("\n".equals(token)) {
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
            list.add(isSelfClosedPTag(token) ? SELF_CLOSED_P_TAG : "");
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

  private static boolean isParaTag(String token) {
    String withoutWS = removeWhiteSpacesFrom(token).toLowerCase(Locale.US);
    return withoutWS.equals(SELF_CLOSED_P_TAG) || withoutWS.equals(P_START_TAG);
  }

  private static boolean isSelfClosedPTag(String token) {
    return removeWhiteSpacesFrom(token).toLowerCase(Locale.US).equals(SELF_CLOSED_P_TAG);
  }

  private static boolean hasLineLongerThan(String str, int maxLength) {
    if (str == null) return false;

    for (String s : str.split("\n")) {
      if (s.length() > maxLength) {
        return true;
      }
    }

    return false;
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
    List<String> list = new ArrayList<>();
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
   * Processes given string and produces on its basis set of pairs like {@code '(string; flag)'} where {@code 'string'}
   * is interested line and {@code 'flag'} indicates if it is wrapped to {@code <pre>} tag.
   *
   * @param s   string to process
   * @return    processing result
   */
  @Nullable
  private List<Pair<String, Boolean>> splitToParagraphs(@Nullable String s) {
    if (s == null) return null;
    s = s.trim();
    if (s.isEmpty()) return null;

    List<Pair<String, Boolean>> result = new ArrayList<>();

    StringBuilder sb = new StringBuilder();
    List<Boolean> markers = new ArrayList<>();
    List<String> list = toArray(s, markers);
    Boolean[] marks = markers.toArray(new Boolean[markers.size()]);
    markers.clear();
    assert list != null;
    for (int i = 0; i < list.size(); i++) {
      String s1 = list.get(i);
      if (marks[i].booleanValue()) {
        if (sb.length() != 0) {
          result.add(new Pair<>(sb.toString(), false));
          sb.setLength(0);
        }
        result.add(Pair.create(s1, marks[i]));
      }
      else {
        if (s1.isEmpty() || s1.equals(SELF_CLOSED_P_TAG) || isKeepLineFeedsIn(s1)) {
          endParagraph(result, sb);
          result.add(Pair.create(s1, marks[i]));
        }
        else {
          if (sb.length() != 0) sb.append(' ');
          sb.append(s1);
        }
      }
    }
    if (!mySettings.JD_PRESERVE_LINE_FEEDS && sb.length() != 0) {
      result.add(new Pair<>(sb.toString(), false));
    }
    return result;
  }

  private boolean isKeepLineFeedsIn(@NotNull String line) {
    return mySettings.JD_PRESERVE_LINE_FEEDS || startsWithTag(line);
  }

  private static boolean startsWithTag(@NotNull String line) {
    if (line.trim().startsWith("<")) {
      return line.matches("\\s*</?\\w+>.*");
    }
    return false;
  }

  private static void endParagraph(@NotNull List<Pair<String, Boolean>> result, @NotNull StringBuilder sb) {
    if (sb.length() > 0) {
      result.add(new Pair<>(sb.toString(), false));
      sb.setLength(0);
    }
  }

  private interface TagParser {
    boolean parse(String tag, String line, JDComment c);
  }

  private static final TagParser[] tagParsers = {
    (tag, line, c) -> {
      boolean isMyTag = JDTag.SEE.tagEqual(tag);
      if (isMyTag) {
        c.addSeeAlso(line);
      }
      return isMyTag;
    },

    (tag, line, c) -> {
      boolean isMyTag = JDTag.SINCE.tagEqual(tag);
      if (isMyTag) {
        c.addSince(line);
      }
      return isMyTag;
    },

    (tag, line, c) -> {
      boolean isMyTag = c instanceof JDClassComment && JDTag.VERSION.tagEqual(tag);
      if (isMyTag) {
        ((JDClassComment)c).setVersion(line);
      }
      return isMyTag;
    },

    (tag, line, c) -> {
      boolean isMyTag = JDTag.DEPRECATED.tagEqual(tag);
      if (isMyTag) {
        c.setDeprecated(line);
      }
      return isMyTag;
    },

    (tag, line, c) -> {
      boolean isMyTag = c instanceof JDMethodComment && JDTag.RETURN.tagEqual(tag);
      if (isMyTag) {
        ((JDMethodComment)c).setReturnTag(line);
      }
      return isMyTag;
    },

    (tag, line, c) -> {
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
    },

    (tag, line, c) -> {
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
    },

    (tag, line, c) -> {
      boolean isMyTag = c instanceof JDClassComment && JDTag.AUTHOR.tagEqual(tag);
      if (isMyTag) {
        ((JDClassComment)c).addAuthor(line.trim());
      }
      return isMyTag;
    }
  };

  private static boolean lineHasUnclosedPreTag(@NotNull String line) {
    return StringUtil.getOccurrenceCount(line, PRE_TAG_START) > StringUtil.getOccurrenceCount(line, PRE_TAG_END);
  }

  private static boolean lineHasClosingPreTag(@NotNull String line) {
    return StringUtil.getOccurrenceCount(line, PRE_TAG_END) > StringUtil.getOccurrenceCount(line, PRE_TAG_START);
  }

  @NotNull
  protected StringBuilder formatJDTagDescription(@Nullable String str, @NotNull CharSequence prefix) {
    return formatJDTagDescription(str, prefix, prefix);
  }

  /**
   * Returns formatted JavaDoc tag description, according to selected configuration. Prefixs
   * may be specified for the first lines and all subsequent lines. This distinction allows
   * partially manual formatting of the first line (by moving content from the description
   * to the first line prefix) and allow continuation lines to use different indentation.
   *
   * @param str                JavaDoc tag description
   * @param firstLinePrefix    prefix to be added to the first line
   * @param continuationPrefix prefix to be added to lines after the first
   * @return formatted JavaDoc tag description
   */
  @NotNull
  protected StringBuilder formatJDTagDescription(@Nullable String str,
                                                 @NotNull CharSequence firstLinePrefix,
                                                 @NotNull CharSequence continuationPrefix) {
    final int rightMargin = myCommonSettings.getRootSettings().getRightMargin(JavaLanguage.INSTANCE);
    final int maxCommentLength = rightMargin - continuationPrefix.length();
    final int firstLinePrefixLength = firstLinePrefix.length();
    final boolean firstLineShorter = firstLinePrefixLength > continuationPrefix.length();

    StringBuilder sb = new StringBuilder(firstLinePrefix);
    List<String> list;

    boolean canWrap = !mySettings.JD_PRESERVE_LINE_FEEDS || hasLineLongerThan(str, maxCommentLength);

    //If wrap comments selected, comments should be wrapped by the right margin
    if (myCommonSettings.WRAP_COMMENTS && canWrap) {
      list = toArrayWrapping(str, maxCommentLength);

      if (firstLineShorter
          && list != null && !list.isEmpty()
          && list.get(0).length() > rightMargin - firstLinePrefixLength)
      {
        list = new ArrayList<>();
        //want the first line to be shorter, according to it's prefix
        String firstLine = toArrayWrapping(str, rightMargin - firstLinePrefixLength).get(0);
        //so now first line is exactly same width we need
        list.add(firstLine);
        str = str.substring(firstLine.length());
        //actually there is one more problem - when first line has unclosed <pre> tag, substring should be processed if it's inside <pre>
        boolean unclosedPreTag = lineHasUnclosedPreTag(firstLine);
        if (unclosedPreTag) {
          str = PRE_TAG_START + str.replaceAll("^\\s+", "");
        }

        //getting all another lines according to their prefix
        List<String> subList = toArrayWrapping(str, maxCommentLength);

        //removing pre tag
        if (unclosedPreTag && subList != null && !subList.isEmpty()) {
          String firstLineTagRemoved = subList.get(0).substring(PRE_TAG_START.length());
          subList.set(0, firstLineTagRemoved);
        }
        if (subList != null) list.addAll(subList);
      }
    }
    else {
      list = toArray(str, new ArrayList<>());
    }

    if (list == null) {
      sb.append('\n');
    }
    else {
      boolean insidePreTag = false;
      for (int i = 0; i < list.size(); i++) {
        String line = list.get(i);
        if (line.isEmpty() && !mySettings.JD_KEEP_EMPTY_LINES) continue;
        if (i != 0) sb.append(continuationPrefix);
        if (line.isEmpty() && mySettings.JD_P_AT_EMPTY_LINES && !insidePreTag) {
          sb.append(P_START_TAG);
        }
        else {
          sb.append(line);

          // We want to track if we're inside <pre>...</pre> in order to not generate <p/> there.
          if (line.startsWith(PRE_TAG_START)) {
            insidePreTag = true;
          }
          else if (line.endsWith(PRE_TAG_END)) {
            insidePreTag = false;
          }
        }
        sb.append('\n');
      }
    }

    return sb;
  }

  private static class CommentInfo {
    public final PsiDocComment docComment;
    public final PsiElement commentOwner;
    public final String commentHeader;
    public final String comment;
    public final String commentFooter;

    public CommentInfo(PsiDocComment docComment, PsiElement commentOwner, String commentHeader, String comment, String commentFooter) {
      this.docComment = docComment;
      this.commentOwner = commentOwner;
      this.commentHeader = commentHeader;
      this.comment = comment;
      this.commentFooter = commentFooter;
    }
  }
}