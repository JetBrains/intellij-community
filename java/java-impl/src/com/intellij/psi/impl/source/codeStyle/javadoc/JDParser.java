// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle.javadoc;

import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.TodoPattern;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
  private final @Nullable JDPreformattingContext myPreformattingContext;

  private static final String SNIPPET_START_REGEXP = "\\{s*@snippet[^\\}]*";
  private static final String PRE_TAG_START_REGEXP = "<pre\\s*(\\w+\\s*=.*)?>";
  private static final Pattern PRE_TAG_START_PATTERN = Pattern.compile(PRE_TAG_START_REGEXP);
  private static final Pattern SNIPPET_START_PATTERN = Pattern.compile(SNIPPET_START_REGEXP);

  // Markdown tokens
  private static final String CODE_FENCE_BACKTICK = "```";
  private static final String CODE_FENCE_TILDE = "~~~";
  private static final String CODE_FENCE_BACKTICK_REGEXP = "`{3,}";
  private static final String CODE_FENCE_TILDE_REGEXP = "~{3,}";
  private static final Pattern CODE_FENCE_TILDE_PATTERN = Pattern.compile(CODE_FENCE_TILDE_REGEXP);
  private static final Pattern CODE_FENCE_BACKTICK_PATTERN = Pattern.compile(CODE_FENCE_BACKTICK_REGEXP);

  private static final String LIST_ITEM_REGEXP = "^\\d+?[).]";
  private static final Pattern LIST_ITEM_PATTERN = Pattern.compile(LIST_ITEM_REGEXP);

  private static final String LIST_ITEM_START = "- ";
  private static final String LIST_ITEM_START_2 = "+ ";
  private static final String LIST_ITEM_START_3 = "* ";
  private static final String LINE_SEPARATOR = "---";
  private static final String HEADER_START = "#";
  private static final String BLOCKQUOTE_START = ">";
  private static final String TABLE_ROW_START = "|";

  private static final String[] TAGS_TO_KEEP_INDENTS_AFTER = {"table", "ol", "ul", "div", "dl"};

  public JDParser(@NotNull CodeStyleSettings settings) {
    this(settings, null);
  }

  public JDParser(@NotNull CodeStyleSettings settings, @Nullable PsiDocComment oldComment) {
    mySettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    myCommonSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    myPreformattingContext = getPreformattingContext(oldComment);
  }

  public @Nullable JDPreformattingContext getPreformattingContext(@Nullable PsiElement element) {
    if (!(element instanceof PsiDocComment comment)) return null;
    DocTextInfo docTextInfo = getDocTextInfo(comment);
    boolean isMarkdown = comment.isMarkdownComment();

    if (!isMarkdown && !JAVADOC_HEADER.equals(docTextInfo.commentHeader)) return null;

    List<Boolean> markers = new ArrayList<>();

    List<String> l = toArray(docTextInfo.comment, markers, isMarkdown);
    if (l == null) return null;

    preprocessLines(l, markers, isMarkdown);

    EmptyLinesInfo emptyLinesInfo = getEmptyLinesInfo(l, isMarkdown);

    if (emptyLinesInfo == null) return null;
    return new JDPreformattingContext(emptyLinesInfo.prefixLinesCount, emptyLinesInfo.suffixLinesCount);
  }

  public void formatCommentText(@NotNull PsiElement element, @NotNull CommentFormatter formatter) {
    CommentInfo info = getElementsCommentInfo(element);
    if (info == null || !isJavadoc(info)) return;

    JDComment comment = parse(info, formatter);
    String indent = formatter.getIndent(info.commentOwner);
    String commentText = comment.generate(indent);
    formatter.replaceCommentText(commentText, info.docComment);
  }

  private static boolean isJavadoc(CommentInfo info) {
    return info.docComment.isMarkdownComment() || JAVADOC_HEADER.equals(info.docTextInfo.commentHeader);
  }

  private static CommentInfo getElementsCommentInfo(@Nullable PsiElement psiElement) {
    if (psiElement instanceof PsiDocComment docComment) {

      PsiJavaDocumentedElement owner = docComment.getOwner();
      if (owner != null) {
        return getCommentInfo(docComment, owner);
      }

      PsiElement parent = docComment.getParent();
      if (parent instanceof PsiJavaFile) {
        return getCommentInfo(docComment, parent);
      }
    }
    else if (psiElement instanceof PsiJavaDocumentedElement owner) {
      PsiDocComment docComment = owner.getDocComment();
      if (docComment != null) {
        return getCommentInfo(docComment, owner);
      }
    }

    return null;
  }

  private static CommentInfo getCommentInfo(@NotNull PsiDocComment docComment, @NotNull PsiElement owner) {
    DocTextInfo docTextInfo = getDocTextInfo(docComment);
    return new CommentInfo(docComment, owner, docTextInfo);
  }

  private static @NotNull JDParser.DocTextInfo getDocTextInfo(@NotNull PsiDocComment docComment) {
    String commentHeader = null;
    String commentFooter = null;

    StringBuilder sb = new StringBuilder();
    String text = docComment.getText();
    if (text.startsWith("///")){
      sb.append(text);
    } else if (text.startsWith("/*")) {
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

    return new DocTextInfo(commentHeader, sb.toString(), commentFooter);
  }

  private @NotNull JDComment parse(@NotNull CommentInfo info, @NotNull CommentFormatter formatter) {
    JDComment comment = createComment(info.commentOwner, formatter, info.docComment.isMarkdownComment());
    parse(info.docTextInfo.comment, comment);
    if (info.docTextInfo.commentHeader != null) {
      comment.setFirstCommentLine(info.docTextInfo.commentHeader);
    }
    if (info.docTextInfo.commentFooter != null) {
      comment.setLastCommentLine(info.docTextInfo.commentFooter);
    }
    return comment;
  }

  private static JDComment createComment(@NotNull PsiElement commentOwner, @NotNull CommentFormatter formatter, boolean isMarkdown) {
    if (commentOwner instanceof PsiClass) {
      return new JDClassComment(formatter, isMarkdown);
    }
    else if (commentOwner instanceof PsiMethod) {
      return new JDMethodComment(formatter, isMarkdown);
    }
    else {
      return new JDComment(formatter, isMarkdown);
    }
  }

  private void parse(@Nullable String text, @NotNull JDComment comment) {
    if (text == null) return;

    List<Boolean> markers = new ArrayList<>();
    List<String> l = toArray(text, markers, comment.getIsMarkdown());

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

    preprocessLines(l, markers, comment.getIsMarkdown());

    if (myPreformattingContext != null) {
      comment.setPrefixEmptyLineCount(myPreformattingContext.getPrefixLinesCount());
      comment.setSuffixEmptyLineCount(myPreformattingContext.getSuffixLinesCount());
    }
    else {
      EmptyLinesInfo emptyLinesInfo = getEmptyLinesInfo(l, comment.getIsMarkdown());
      if (emptyLinesInfo != null) {
      comment.setPrefixEmptyLineCount(emptyLinesInfo.prefixLinesCount);
      comment.setSuffixEmptyLineCount(emptyLinesInfo.suffixLinesCount);
      }
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
          if (!sb.isEmpty()) {
            sb.append('\n');
          }
          sb.append(line);
        }
      }
      else {
        if (!sb.isEmpty()) {
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

  private @Nullable EmptyLinesInfo getEmptyLinesInfo(@NotNull List<String> l, boolean isMarkdown) {
    if (!mySettings.shouldKeepEmptyTrailingLines()) return null;
    // counting for the empty lines in the prefix and the suffix of the javadoc to restore them in the future
    int prefixLineCount = 0;
    int suffixLineCount = 0;
    int size = l.size();
    while (prefixLineCount < size && l.get(prefixLineCount).isEmpty()) prefixLineCount++;
    if (prefixLineCount == size) {
      if (isMarkdown) prefixLineCount--;

      return new EmptyLinesInfo(prefixLineCount, 0);
    }
    else {
      while (suffixLineCount < size && l.get(size - suffixLineCount - 1).isEmpty()) suffixLineCount++;

      return new EmptyLinesInfo(prefixLineCount, suffixLineCount);
    }
  }

  private static void preprocessLines(List<String> l, List<Boolean> markers, boolean isMarkdown) {
    // preprocess strings - removes leading token
    for (int i = 0; i < l.size(); i++) {
      String line = l.get(i);
      line = line.trim();
      if (!line.isEmpty()) {
        if(!isMarkdown){
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
        } else {
          // Note: Markdown comments are not trimmed like html ones, except for javadoc tags
          String newLine;
          int tagStart = CharArrayUtil.shiftForward(line, 3, " \t");
          if (tagStart != line.length() && line.charAt(tagStart) == '@') {
            newLine = line.substring(tagStart);
          } else {
            newLine = StringUtil.trimStart(line, "/// ");
            if (Strings.areSameInstance(newLine, line)) {
              newLine = StringUtil.trimStart(line, "///");
            }
          }
          line = newLine;
        }
      }
      l.set(i, line);
    }
  }

  /**
   * Breaks the specified string by the specified separators into array of strings
   *
   * @param s       the specified string
   * @param markers if this parameter is not null then it will be filled with Boolean values:
   *                true if the corresponding line in returned list is inside &lt;pre&gt; tag or a markdown codeblock,
   *                false if it is outside
   * @return list of strings (lines)
   */
  private @Nullable List<String> toArray(@Nullable String s, @Nullable List<Boolean> markers, boolean markdownComment) {
    if (s == null) return null;
    s = markdownComment ? s.stripTrailing() : s.strip();
    if (s.isEmpty()) return null;
    boolean p2nl = markers != null && mySettings.JD_P_AT_EMPTY_LINES && !markdownComment;
    List<String> list = new ArrayList<>();
    StringTokenizer st = new StringTokenizer(s, "\n", true);
    boolean first = true;
    int preCount = 0;
    int curPos = 0;
    int firstLineToKeepIndents = -1;
    int minIndentWhitespaces = Integer.MAX_VALUE;
    boolean isInMultilineTodo = false;
    boolean isInSnippet = false;
    int snippetBraceBalance = 0;
    String codeblockType = null;

    while (st.hasMoreTokens()) {
      String token = st.nextToken();
      curPos += token.length();

      String lineWithoutAsterisk = getLineWithoutLeadingTokens(token, markdownComment);
      if (!isInMultilineTodo) {
        if (isMultilineTodoStart(lineWithoutAsterisk)) {
          isInMultilineTodo = true;
        }
        else if (containsTagToKeepIndentsAfter(lineWithoutAsterisk) && firstLineToKeepIndents < 0) {
          firstLineToKeepIndents = list.size();
        }
      }

      if (firstLineToKeepIndents >= 0) {
        minIndentWhitespaces = Math.min(getIndentWhitespaces(token), minIndentWhitespaces);
      }

      if ("\n".equals(token)) {
        if (!first) {
          list.add("");
          if (markers != null) markers.add(preCount > 0 || firstLineToKeepIndents >= 0 || isInMultilineTodo);
        }
        first = false;
      }
      else {
        first = true;
        if (isInMultilineTodo && StringUtil.isEmpty(lineWithoutAsterisk)) {
          isInMultilineTodo = false;
        }
        if (p2nl && isParaTag(token) && s.indexOf(P_END_TAG, curPos) < 0) {
          list.add(isSelfClosedPTag(token) ? SELF_CLOSED_P_TAG : P_START_TAG);
          markers.add(preCount > 0 || firstLineToKeepIndents >= 0);
          continue;
        }

        if (!markdownComment && preCount == 0 && firstLineToKeepIndents < 0 && !isInMultilineTodo && snippetBraceBalance == 0) {
          token = token.trim();
        }

        list.add(token);

        if (markers != null) {
          if (snippetBraceBalance == 0) {
            if (lineHasUnclosedSnippetTag(token)) snippetBraceBalance = 1;
          } else {
            snippetBraceBalance += getLineSnippetTagBraceBalance(token);
          }
          if (lineHasUnclosedPreTag(token)) preCount++;
          if (markdownComment && codeblockType == null) {
            codeblockType = lineGetStartCodeFence(token);
            if (codeblockType != null) preCount++;
          }

          markers.add(preCount > 0
                      || firstLineToKeepIndents >= 0
                      || isInMultilineTodo
                      || snippetBraceBalance != 0
                      || (markdownComment && isStartOfMarkdownHeader(token)) /* Markdown titles cannot be split */);

          if (lineHasClosingPreTag(token)) preCount--;
          if (markdownComment && codeblockType != null) {
            if (Strings.areSameInstance(codeblockType, CODE_FENCE_BACKTICK) && token.contains(CODE_FENCE_BACKTICK)
                || Strings.areSameInstance(codeblockType, CODE_FENCE_TILDE) && token.contains(CODE_FENCE_TILDE)) {
              preCount--;
              codeblockType = null;
            }
          }
        }

      }
    }

    if (minIndentWhitespaces > 0 && minIndentWhitespaces < Integer.MAX_VALUE) {
      for (int i = firstLineToKeepIndents; i < list.size(); i ++) {
        String line = list.get(i);
        if (!line.trim().isEmpty()) {
          if (line.length() > minIndentWhitespaces) {
            list.set(i, line.substring(minIndentWhitespaces));
          }
        }
      }
    }
    return list;
  }

  private static boolean containsTagToKeepIndentsAfter(@NotNull String line) {
    String tag = HtmlUtil.getStartTag(line);
    return tag != null && ArrayUtil.contains(tag, TAGS_TO_KEEP_INDENTS_AFTER);
  }

  private static boolean isMultilineTodoStart(@NotNull String line) {
    if (TodoConfiguration.getInstance().isMultiLine()) {
      for (TodoPattern todoPattern : TodoConfiguration.getInstance().getTodoPatterns()) {
        Pattern p = todoPattern.getPattern();
        if (p != null && p.matcher(line.trim()).matches()) {
          return true;
        }
      }
    }
    return false;
  }

  private static int getIndentWhitespaces(@NotNull String line) {
    int indentWhitespaces = 0;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      switch (c) {
        case ' ', '\t' -> indentWhitespaces++;
        case '\n' -> {
          return Integer.MAX_VALUE;
        }
        default -> {
          return indentWhitespaces;
        }
      }
    }
    return Integer.MAX_VALUE;
  }

  private static String getLineWithoutLeadingTokens(@NotNull String line, boolean markdownComment) {
    String leadingToken = markdownComment ? "///" : "*";
    int asteriskPos = line.indexOf(leadingToken);
    return asteriskPos >= 0 ? line.substring(asteriskPos + leadingToken.length()) : line;
  }

  private static boolean isParaTag(String token) {
    String withoutWS = StringUtil.toLowerCase(removeWhiteSpacesFrom(token));
    return withoutWS.equals(SELF_CLOSED_P_TAG) || withoutWS.equals(P_START_TAG);
  }

  private static boolean isSelfClosedPTag(String token) {
    return StringUtil.toLowerCase(removeWhiteSpacesFrom(token)).equals(SELF_CLOSED_P_TAG);
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

  private static @NotNull String removeWhiteSpacesFrom(final @NotNull String token) {
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
   * @return list of strings (lines)
   */
  @Contract("null, _, _ -> null")
  private List<String> toArrayWrapping(@Nullable String s, int width, boolean markdownComment) {
    List<String> list = new ArrayList<>();
    List<Pair<String, Boolean>> pairs = splitToParagraphs(s, markdownComment);
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
        if (seq.length() < width || isMarked) {
          // keep remaining line and proceed with next paragraph
          seq = isMarked || markdownComment ? seq : seq.trim();
          list.add(seq);
          break;
        }
        else {
          // wrap paragraph

          int wrapPos = computeWrapPosition(seq, width);

          // wrap now
          if (wrapPos >= seq.length() - 1 || wrapPos < 0) {
            seq = seq.trim();
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
   * Chooses the point within the string at which to wrap the line. Wrapping is always done at a
   * space character with a preference to not splitting inline JavaDoc tags in the process. The
   * position returned is the greatest position, less than or equal to the given width, which does
   * not fall within an inline tag.
   *
   * <p>If no such position exists it can be for one of two reasons,
   * <ol>
   *   <li>The line begins with a long inline tag which exceeds the right margin.</li>
   *   <li>The line begins with a long unbroken string (e.g. a URL) which exceeds the right margin.</li>
   * </ol>
   * </p>
   *
   * If the first case we ignore the preference to not split tags and do so regardless. In the
   * second case we allow the line to run over the margin and split at the first available position.
   */
  private static int computeWrapPosition(String line, int width) {
    if (line.length() < width) {
      return line.length();
    }

    int preferredBreakPoint = -1;
    int backupBreakPoint = -1;
    int tagBraceBalance = 0;

    for (int i = 0; i < line.length() && (i <= width || backupBreakPoint < 0); i++) {
      char c = line.charAt(i);
      if (tagBraceBalance > 0) {
        if (c == '{') {
          tagBraceBalance++;
        }
        else if (c == '}') {
          tagBraceBalance--;
        }
      }
      else if (c == '@' && i > 0 && line.charAt(i - 1) == '{') {
        // We're now inside of an inline tag. Start keeping track of the balance
        // of opening and closing braces to determine when we've left the tag.
        tagBraceBalance++;
      }
      else if (c == ' ') {
        preferredBreakPoint = i;
      }

      // We'll use this position in a pitch if we can't break somewhere not inside a tag.
      if (c == ' ') {
        backupBreakPoint = i;
      }
    }

    if (preferredBreakPoint > 0) {
      return preferredBreakPoint;
    }
    if (backupBreakPoint > 0) {
      return backupBreakPoint;
    }
    return line.length();
  }

  /**
   * Processes given string and produces on its basis set of pairs like {@code '(string; flag)'} where {@code 'string'}
   * is interested line and {@code 'flag'} indicates if it is wrapped to {@code <pre>} tag.
   *
   * @param s   string to process
   * @return    processing result
   */
  @Contract("null, _ -> null")
  private List<Pair<String, Boolean>> splitToParagraphs(@Nullable String s, boolean markdownComment) {
    if (s == null) return null;
    s = markdownComment ? s : s.trim();
    if (s.isEmpty()) return null;

    List<Pair<String, Boolean>> result = new ArrayList<>();

    StringBuilder sb = new StringBuilder();
    List<Boolean> markers = new ArrayList<>();
    List<String> list = toArray(s, markers, markdownComment);
    Boolean[] marks = markers.toArray(new Boolean[0]);
    markers.clear();
    assert list != null;
    for (int i = 0; i < list.size(); i++) {
      String s1 = list.get(i);
      if (marks[i].booleanValue()) {
        endParagraph(result, sb);
        result.add(Pair.create(s1, marks[i]));
      }
      else {
        if (s1.isEmpty() || s1.equals(SELF_CLOSED_P_TAG) || isKeepLineFeedsIn(s1)) {
          endParagraph(result, sb);
          result.add(Pair.create(s1, marks[i]));
        }
        else {
          if (markdownComment && isStartOfMarkdownConstruct(s1)) {
            endParagraph(result, sb);
          }

          if (!sb.isEmpty()) sb.append(' ');
          if (markdownComment && !sb.isEmpty()) {
            // When fusing lines together, horizontal spacing loses its meaning
            sb.append(s1.trim());
          }
          else {
            sb.append(s1);
          }
        }
      }
    }
    if (!mySettings.JD_PRESERVE_LINE_FEEDS && !sb.isEmpty()) {
      result.add(new Pair<>(sb.toString(), false));
    }
    return result;
  }

  private boolean isKeepLineFeedsIn(@NotNull String line) {
    return mySettings.JD_PRESERVE_LINE_FEEDS || HtmlUtil.startsWithTag(line);
  }

  /**
   * @return Whether there is a star of a markdown construct
   * That should not be merged into a single paragraph
   */
  private static boolean isStartOfMarkdownConstruct(@NotNull String line) {
    String trimmedLine = line.trim();
    return trimmedLine.startsWith(BLOCKQUOTE_START)
           || trimmedLine.startsWith(LINE_SEPARATOR)
           || isStartOfMarkdownHeader(trimmedLine)
           || isMarkdownTableRow(trimmedLine)
           || isStartOfMarkdownListItem(trimmedLine);
  }

  private static boolean isStartOfMarkdownHeader(@NotNull String line) {
    return line.trim().startsWith(HEADER_START);
  }

  private static boolean isStartOfMarkdownListItem(@NotNull String line) {
    return line.startsWith(LIST_ITEM_START)
           || line.startsWith(LIST_ITEM_START_2)
           || line.startsWith(LIST_ITEM_START_3)
           || LIST_ITEM_PATTERN.matcher(line).find();
  }

  private static boolean isMarkdownTableRow(@NotNull String line) {
    return line.startsWith(TABLE_ROW_START) && StringUtil.getOccurrenceCount(line, TABLE_ROW_START) > 1;
  }

  private static void endParagraph(@NotNull List<? super Pair<String, Boolean>> result, @NotNull StringBuilder sb) {
    if (!sb.isEmpty()) {
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
        ((JDMethodComment)c).addReturnTag(line);
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
    return getOccurenceCount(line, PRE_TAG_START_PATTERN) > StringUtil.getOccurrenceCount(line, PRE_TAG_END);
  }

  private static int getLineSnippetTagBraceBalance(@NotNull String line) {
    int balance = 0;
    for (int i = 0; i < line.length(); i++) {
      var ch = line.charAt(i);
      if (ch == '}') {
        balance--;
      } else if (ch == '{') {
        balance++;
      }
    }
    return balance;
  }

  private static boolean lineHasUnclosedSnippetTag(@NotNull String line) {
    var matcher = SNIPPET_START_PATTERN.matcher(line);
    var hasResult = false;
    var lastEnd = -1;
    do {
      hasResult = matcher.find();
      if (hasResult) {
        lastEnd = matcher.end();
      }
    } while (hasResult);
    return lastEnd == line.length();
  }

  private static boolean lineHasClosingPreTag(@NotNull String line) {
    return StringUtil.getOccurrenceCount(line, PRE_TAG_END) > getOccurenceCount(line, PRE_TAG_START_PATTERN);
  }


  /**
   * Detects a stating code block fence in a given line
   *
   * @return The code fence type, or null if none are found
   */
  private static String lineGetStartCodeFence(@NotNull String line) {
    int backticks = getOccurenceCount(line, CODE_FENCE_BACKTICK_PATTERN);
    int tildes = getOccurenceCount(line, CODE_FENCE_TILDE_PATTERN);

    boolean hasBacktickStart = backticks % 2 != 0;
    boolean hasTildeStart = tildes % 2 != 0;

    if (hasBacktickStart && hasTildeStart) {
      if (backticks == tildes) {
        // find the first fence
        int backtickIndex = line.lastIndexOf(CODE_FENCE_BACKTICK);
        int tildeIndex = line.lastIndexOf(CODE_FENCE_TILDE);
        return backtickIndex > tildeIndex ? CODE_FENCE_TILDE : CODE_FENCE_BACKTICK;
      }
      else {
        // give back whoever has the least amount of occurrences
        return backticks > tildes ? CODE_FENCE_TILDE : CODE_FENCE_BACKTICK;
      }
    }
    else if (hasTildeStart) {
      return CODE_FENCE_BACKTICK;
    }
    else if (hasBacktickStart) {
      return CODE_FENCE_TILDE;
    }
    return null;
  }


  @SuppressWarnings("SameParameterValue")
  private static int getOccurenceCount(@NotNull String line, @NotNull Pattern pattern) {
    Matcher matcher = pattern.matcher(line);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  protected @NotNull StringBuilder formatJDTagDescription(@Nullable String str, @NotNull CharSequence prefix, boolean markdownComment) {
    return formatJDTagDescription(str, prefix, prefix, markdownComment);
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
  protected @NotNull StringBuilder formatJDTagDescription(@Nullable String str,
                                                 @NotNull CharSequence firstLinePrefix,
                                                 @NotNull CharSequence continuationPrefix,
                                                          boolean markdownComment) {
    final int rightMargin = myCommonSettings.getRootSettings().getRightMargin(JavaLanguage.INSTANCE);
    final int maxCommentLength = rightMargin - continuationPrefix.length();
    final int firstLinePrefixLength = firstLinePrefix.length();
    final boolean firstLineShorter = firstLinePrefixLength > continuationPrefix.length();

    StringBuilder sb = new StringBuilder(firstLinePrefix);
    List<String> list;

    boolean canWrap = !mySettings.JD_PRESERVE_LINE_FEEDS || hasLineLongerThan(str, maxCommentLength);

    //If wrap comments selected, comments should be wrapped by the right margin
    if (myCommonSettings.WRAP_COMMENTS && canWrap) {
      list = toArrayWrapping(str, maxCommentLength, markdownComment);

      if (firstLineShorter
          && list != null && !list.isEmpty()
          && list.get(0).length() > rightMargin - firstLinePrefixLength)
      {
        list = new ArrayList<>();
        //want the first line to be shorter, according to it's prefix
        String firstLine = toArrayWrapping(str, rightMargin - firstLinePrefixLength, markdownComment).get(0);
        //so now first line is exactly same width we need
        list.add(firstLine);
        str = str.substring(firstLine.length());
        //actually there is one more problem - when first line has unclosed <pre> tag, substring should be processed if it's inside <pre>
        boolean unclosedPreTag = lineHasUnclosedPreTag(firstLine);
        if (unclosedPreTag) {
          str = PRE_TAG_START + str.replaceAll("^\\s+", "");
        }

        //getting all another lines according to their prefix
        List<String> subList = toArrayWrapping(str, maxCommentLength, markdownComment);

        //removing pre tag
        if (unclosedPreTag && subList != null && !subList.isEmpty()) {
          String firstLineTagRemoved = subList.get(0).substring(PRE_TAG_START.length());
          subList.set(0, firstLineTagRemoved);
        }
        if (subList != null) list.addAll(subList);
      }
    }
    else {
      list = toArray(str, new ArrayList<>(), markdownComment);
    }

    if (list == null) {
      sb.append('\n');
    }
    else {
      int snippetBraceBalance = 0;
      boolean insidePreTag = false;
      for (int i = 0; i < list.size(); i++) {
        String line = list.get(i);
        if (line.isEmpty() && !mySettings.JD_KEEP_EMPTY_LINES) continue;
        if (i != 0) sb.append(continuationPrefix);
        if (!markdownComment && line.isEmpty() && mySettings.JD_P_AT_EMPTY_LINES && !insidePreTag && !isFollowedByTagLine(list, i) && snippetBraceBalance == 0) {
          sb.append(P_START_TAG);
        }
        else {
          sb.append(line);

          // We want to track if we're inside <pre>...</pre> in order to not generate <p/> there.
          if (lineHasUnclosedPreTag(line)) {
            insidePreTag = true;
          }
          else if (lineHasClosingPreTag(line)) {
            insidePreTag = false;
          }

          if (snippetBraceBalance == 0) {
            if (lineHasUnclosedSnippetTag(line)) snippetBraceBalance = 1;
          } else {
            snippetBraceBalance += getLineSnippetTagBraceBalance(line);
          }
        }
        sb.append('\n');
      }
    }

    return sb;
  }

  private static boolean isFollowedByTagLine(List<String> lines, int currLine) {
    for (int i = currLine + 1; i < lines.size(); i ++) {
      String line = lines.get(i);
      if (!line.isEmpty()) {
        return HtmlUtil.startsWithTag(line);
      }
    }
    return false;
  }

  private record CommentInfo(PsiDocComment docComment, PsiElement commentOwner, DocTextInfo docTextInfo) {
  }
  
  private record DocTextInfo(String commentHeader, String comment, String commentFooter) {
  }

  private record EmptyLinesInfo(int prefixLinesCount, int suffixLinesCount) {
  }
}