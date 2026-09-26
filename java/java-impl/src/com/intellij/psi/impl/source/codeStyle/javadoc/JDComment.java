// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle.javadoc;

import com.intellij.formatting.IndentInfo;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Skavish
 */
public class JDComment {
  protected final CommentFormatter myFormatter;

  private int myPrefixEmptyLineCount = 0;
  private int mySuffixEmptyLineCount = 0;
  private String myDescription;
  private List<String> myUnknownList;
  private List<String> mySeeAlsoList;
  private List<String> mySinceList;
  private String myDeprecated;
  private boolean myMultiLineComment;

  private final boolean myMarkdown;
  private String myFirstLine;
  private String myEndLine;
  private final String myLeadingLine;


  public JDComment(@NotNull CommentFormatter formatter, boolean isMarkdown) {
    myFormatter = formatter;
    myMarkdown = isMarkdown;

    myFirstLine = myMarkdown ? "///" : "/**";
    myEndLine =  myMarkdown ? "" : "*/";
    myLeadingLine = myMarkdown ? "/// " : " * ";
  }

  protected static boolean isNull(@Nullable String s) {
    return s == null || s.trim().isEmpty();
  }

  protected static boolean isNull(@Nullable List<?> l) {
    return l == null || l.isEmpty();
  }

  public void setMultiLine(boolean value) {
    myMultiLineComment = value;
  }

  protected @NotNull String javadocContinuationIndent() {
    if (!myFormatter.getSettings().JD_INDENT_ON_CONTINUATION) return "";
    return continuationIndent();
  }

  protected @NotNull String continuationIndent() {
    CodeStyleSettings settings = myFormatter.getSettings().getContainer();
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions(JavaFileType.INSTANCE);
    return new IndentInfo(0, indentOptions.CONTINUATION_INDENT_SIZE, 0).generateNewWhiteSpace(indentOptions);
  }

  public @Nullable String generate(@NotNull String indent) {
    final String prefix;

    if (myMarkdown || myFormatter.getSettings().JD_LEADING_ASTERISKS_ARE_ENABLED) {
      prefix = indent + myLeadingLine;
    } else {
      prefix = indent;
    }

    StringBuilder sb = new StringBuilder();

    if (!isNull(myDescription)) {
      sb.append(myFormatter.getParser().formatJDTagDescription(myDescription, prefix, getIsMarkdown()));

      if (myFormatter.getSettings().JD_ADD_BLANK_AFTER_DESCRIPTION) {
        sb.append(prefix);
        sb.append('\n');
      }
    }

    generateSpecial(prefix, sb);

    final String continuationPrefix = prefix + javadocContinuationIndent();

    if (!isNull(myUnknownList) && myFormatter.getSettings().JD_KEEP_INVALID_TAGS) {
      for (String aUnknownList : myUnknownList) {
        sb.append(myFormatter.getParser().formatJDTagDescription(aUnknownList, prefix, continuationPrefix, getIsMarkdown()));
      }
    }

    if (!isNull(mySeeAlsoList)) {
      JDTag tag = JDTag.SEE;
      for (String aSeeAlsoList : mySeeAlsoList) {
        StringBuilder tagDescription = myFormatter.getParser()
          .formatJDTagDescription(aSeeAlsoList, prefix + tag.getWithEndWhitespace(), continuationPrefix, getIsMarkdown());
        sb.append(tagDescription);
      }
    }

    if (!isNull(mySinceList)) {
      JDTag tag = JDTag.SINCE;
      for (String since : mySinceList) {
        StringBuilder tagDescription = myFormatter.getParser()
          .formatJDTagDescription(since, prefix + tag.getWithEndWhitespace(), continuationPrefix, getIsMarkdown());
        sb.append(tagDescription);
      }
    }

    if (myDeprecated != null) {
      JDTag tag = JDTag.DEPRECATED;
      StringBuilder tagDescription = myFormatter.getParser()
        .formatJDTagDescription(myDeprecated, prefix + tag.getWithEndWhitespace(), continuationPrefix, getIsMarkdown());
      sb.append(tagDescription);
    }

    String emptyLine = prefix + '\n';

    if (sb.length() > prefix.length()) {
      // if it ends with a blank line delete that
      int nlen = sb.length() - prefix.length() - 1;
      if (sb.substring(nlen, sb.length()).equals(prefix + "\n")) {
        sb.delete(nlen, sb.length());
      }
    }
    else if (sb.isEmpty() && !StringUtil.isEmpty(myEndLine) && !hasEmptyTrimmedLines()) {
      sb.append(emptyLine);
    }


    if (!myMarkdown && (myMultiLineComment &&
        myFormatter.getSettings().JD_DO_NOT_WRAP_ONE_LINE_COMMENTS
        || !myFormatter.getSettings().JD_DO_NOT_WRAP_ONE_LINE_COMMENTS
        || sb.indexOf("\n") != sb.length() - 1)) // If comment has become multiline after formatting - it must be shown as multiline.
                                                // Last symbol is always '\n', so we need to check if there is one more LF symbol before it.
    {
      addPrefixEmptyLinesIfNeeded(sb, emptyLine, indent);
      sb.insert(0, myFirstLine + '\n');
      sb.append(indent);
    } else {
      sb.replace(0, prefix.length(), myFirstLine + " ");
      sb.deleteCharAt(sb.length()-1);
      addPrefixEmptyLinesIfNeeded(sb, emptyLine, indent);
    }

    addSuffixEmptyLinesIfNeeded(sb, emptyLine);

    sb.append(' ').append(myEndLine);

    return sb.toString();
  }

  private void addPrefixEmptyLinesIfNeeded(@NotNull StringBuilder sb, @NotNull String emptyLine, @NotNull String indent) {
    StringBuilder emptyLinePrefixBuilder = new StringBuilder();
    addEmptyLinesIfNeeded(emptyLinePrefixBuilder, emptyLine, myPrefixEmptyLineCount);
    if (myMarkdown && shouldAddExtraLines(myPrefixEmptyLineCount)) {
      emptyLinePrefixBuilder.append(indent);
      emptyLinePrefixBuilder.delete(0, indent.length());
    }
    sb.insert(0, emptyLinePrefixBuilder);
  }

  private void addSuffixEmptyLinesIfNeeded(@NotNull StringBuilder sb, @NotNull String prefix) {
    addEmptyLinesIfNeeded(sb, prefix, mySuffixEmptyLineCount);
    if (myMarkdown && shouldAddExtraLines(mySuffixEmptyLineCount)) {
      while (!sb.isEmpty() && Character.isWhitespace(sb.charAt(sb.length() - 1))) {
        sb.deleteCharAt(sb.length() - 1);
      }
    }
  }

  private void addEmptyLinesIfNeeded(StringBuilder sb, String emptyLine, int lineCount) {
    if (shouldAddExtraLines(lineCount)) {
      int lastSymbolIndex = sb.length() - 1;
      if (!sb.isEmpty() && sb.charAt(lastSymbolIndex) != '\n') sb.append('\n');
      sb.append(String.valueOf(emptyLine).repeat(lineCount));
    }
  }

  private boolean shouldAddExtraLines(int lineCount) {
    return lineCount > 0 && myFormatter.getSettings().shouldKeepEmptyTrailingLines();
  }

  private boolean hasEmptyTrimmedLines() {
    return myPrefixEmptyLineCount != 0 || mySuffixEmptyLineCount != 0;
  }

  protected void generateSpecial(@NotNull String prefix, @NotNull StringBuilder sb) {
  }

  public void setFirstCommentLine(@NotNull String firstCommentLine) {
    myFirstLine = firstCommentLine;
  }

  public void setLastCommentLine(@NotNull String lastCommentLine) {
    myEndLine = lastCommentLine;
  }

  public void addSeeAlso(@NotNull String seeAlso) {
    if (mySeeAlsoList == null) mySeeAlsoList = new ArrayList<>();
    mySeeAlsoList.add(seeAlso);
  }

  public void addUnknownTag(@NotNull String unknownTag) {
    if (myUnknownList == null) myUnknownList = new ArrayList<>();
    myUnknownList.add(unknownTag);
  }

  public void addSince(@NotNull String since) {
    if (mySinceList == null) mySinceList = new ArrayList<>();
    mySinceList.add(since);
  }

  public void setPrefixEmptyLineCount(int prefixEmptyLineCount) {
    this.myPrefixEmptyLineCount = prefixEmptyLineCount;
  }

  public void setSuffixEmptyLineCount(int suffixEmptyLineCount) {
    this.mySuffixEmptyLineCount = suffixEmptyLineCount;
  }

  public boolean getIsMarkdown() {
    return myMarkdown;
  }

  public void setDeprecated(@Nullable String deprecated) {
    this.myDeprecated = deprecated;
  }

  public @Nullable String getDescription() {
    return myDescription;
  }

  public void setDescription(@Nullable String description) {
    this.myDescription = description;
  }
}
