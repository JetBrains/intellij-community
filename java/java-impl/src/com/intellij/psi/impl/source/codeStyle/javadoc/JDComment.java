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

import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author max
 */

/**
 * @author Dmitry Skavish
 */
public class JDComment {
  protected final CommentFormatter myFormatter;

  private String myDescription;
  private List<String> myUnknownList;
  private List<String> mySeeAlsoList;
  private String mySince;
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
      sb.append(prefix);
      sb.append(myFormatter.getParser().formatJDTagDescription(myDescription, prefix));

      if (myFormatter.getSettings().JD_ADD_BLANK_AFTER_DESCRIPTION) {
        sb.append(prefix);
        sb.append('\n');
      }
    }

    generateSpecial(prefix, sb);

    if (!isNull(myUnknownList) && myFormatter.getSettings().JD_KEEP_INVALID_TAGS) {
      for (String aUnknownList : myUnknownList) {
        sb.append(prefix);
        sb.append(myFormatter.getParser().formatJDTagDescription(aUnknownList, prefix));
      }
    }

    if (!isNull(mySeeAlsoList)) {
      JDTag tag = JDTag.SEE;
      for (String aSeeAlsoList : mySeeAlsoList) {
        sb.append(prefix);
        sb.append(tag.getWithEndWhitespace());
        StringBuilder tagDescription = myFormatter.getParser()
          .formatJDTagDescription(aSeeAlsoList, prefix, true, tag.getDescriptionPrefix(prefix).length());
        sb.append(tagDescription);
      }
    }

    if (!isNull(mySince)) {
      JDTag tag = JDTag.SINCE;
      sb.append(prefix);
      sb.append(tag.getWithEndWhitespace());
      StringBuilder tagDescription = myFormatter.getParser()
        .formatJDTagDescription(mySince, prefix, true, tag.getDescriptionPrefix(prefix).length());
      sb.append(tagDescription);
    }

    if (myDeprecated != null) {
      JDTag tag = JDTag.DEPRECATED;
      sb.append(prefix);
      sb.append(tag.getWithEndWhitespace());
      StringBuilder tagDescription = myFormatter.getParser()
        .formatJDTagDescription(myDeprecated, prefix, true, tag.getDescriptionPrefix(prefix).length());
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
    if (mySeeAlsoList == null) {
      mySeeAlsoList = ContainerUtilRt.newArrayList();
    }
    mySeeAlsoList.add(seeAlso);
  }

  public void addUnknownTag(@NotNull String unknownTag) {
    if (myUnknownList == null) {
      myUnknownList = ContainerUtilRt.newArrayList();
    }
    myUnknownList.add(unknownTag);
  }

  public void setSince(@Nullable String since) {
    this.mySince = since;
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
