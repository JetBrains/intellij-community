/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.formatting.IndentInfo;
import com.intellij.ide.highlighter.JavaFileType;
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

  private String myDescription;
  private List<String> myUnknownList;
  private List<String> mySeeAlsoList;
  private List<String> mySinceList;
  private String myDeprecated;
  private boolean myMultiLineComment;
  private String myFirstLine = "/**";
  private String myEndLine = "*/";

  public JDComment(@NotNull CommentFormatter formatter) {
    myFormatter = formatter;
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

  @NotNull
  protected String javadocContinuationIndent() {
    if (!myFormatter.getSettings().JD_INDENT_ON_CONTINUATION) return "";
    return continuationIndent();
  }

  @NotNull
  protected String continuationIndent() {
    CodeStyleSettings settings = myFormatter.getSettings().getContainer();
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions(JavaFileType.INSTANCE);
    return new IndentInfo(0, indentOptions.CONTINUATION_INDENT_SIZE, 0).generateNewWhiteSpace(indentOptions);
  }

  @Nullable
  public String generate(@NotNull String indent) {
    final String prefix;

    if (myFormatter.getSettings().JD_LEADING_ASTERISKS_ARE_ENABLED) {
      prefix = indent + " * ";
    } else {
      prefix = indent;
    }

    StringBuilder sb = new StringBuilder();
    int start = sb.length();

    if (!isNull(myDescription)) {
      sb.append(myFormatter.getParser().formatJDTagDescription(myDescription, prefix));

      if (myFormatter.getSettings().JD_ADD_BLANK_AFTER_DESCRIPTION) {
        sb.append(prefix);
        sb.append('\n');
      }
    }

    generateSpecial(prefix, sb);

    final String continuationPrefix = prefix + javadocContinuationIndent();

    if (!isNull(myUnknownList) && myFormatter.getSettings().JD_KEEP_INVALID_TAGS) {
      for (String aUnknownList : myUnknownList) {
        sb.append(myFormatter.getParser().formatJDTagDescription(aUnknownList, prefix, continuationPrefix));
      }
    }

    if (!isNull(mySeeAlsoList)) {
      JDTag tag = JDTag.SEE;
      for (String aSeeAlsoList : mySeeAlsoList) {
        StringBuilder tagDescription = myFormatter.getParser()
          .formatJDTagDescription(aSeeAlsoList, prefix + tag.getWithEndWhitespace(), continuationPrefix);
        sb.append(tagDescription);
      }
    }

    if (!isNull(mySinceList)) {
      JDTag tag = JDTag.SINCE;
      for (String since : mySinceList) {
        StringBuilder tagDescription = myFormatter.getParser()
          .formatJDTagDescription(since, prefix + tag.getWithEndWhitespace(), continuationPrefix);
        sb.append(tagDescription);
      }
    }

    if (myDeprecated != null) {
      JDTag tag = JDTag.DEPRECATED;
      StringBuilder tagDescription = myFormatter.getParser()
        .formatJDTagDescription(myDeprecated, prefix + tag.getWithEndWhitespace(), continuationPrefix);
      sb.append(tagDescription);
    }

    if (sb.length() == start) return null;

    // if it ends with a blank line delete that
    int nlen = sb.length() - prefix.length() - 1;
    if (sb.substring(nlen, sb.length()).equals(prefix + "\n")) {
      sb.delete(nlen, sb.length());
    }

    if (myMultiLineComment && myFormatter.getSettings().JD_DO_NOT_WRAP_ONE_LINE_COMMENTS
        || !myFormatter.getSettings().JD_DO_NOT_WRAP_ONE_LINE_COMMENTS
        || sb.indexOf("\n") != sb.length() - 1) // If comment has become multiline after formatting - it must be shown as multiline.
                                                // Last symbol is always '\n', so we need to check if there is one more LF symbol before it.
    {
      sb.insert(0, myFirstLine + '\n');
      sb.append(indent);
    } else {
      sb.replace(0, prefix.length(), myFirstLine + " ");
      sb.deleteCharAt(sb.length()-1);
    }
    sb.append(' ').append(myEndLine);

    return sb.toString();
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

  public void setDeprecated(@Nullable String deprecated) {
    this.myDeprecated = deprecated;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  public void setDescription(@Nullable String description) {
    this.myDescription = description;
  }
}
