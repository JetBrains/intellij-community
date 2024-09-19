// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle.javadoc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Class comment
 *
 * @author Dmitry Skavish
 */
public class JDClassComment extends JDParamListOwnerComment {
  private List<String> myAuthorsList;
  private String myVersion;

  public JDClassComment(@NotNull CommentFormatter formatter, boolean isMarkdown) {
    super(formatter, isMarkdown);
  }

  @Override
  protected void generateSpecial(@NotNull String prefix, @NotNull StringBuilder sb) {
    super.generateSpecial(prefix, sb);
    String continuationPrefix = prefix + javadocContinuationIndent();
    if (!isNull(myAuthorsList)) {
      JDTag tag = JDTag.AUTHOR;
      for (String author : myAuthorsList) {
        sb.append(myFormatter.getParser().formatJDTagDescription(author,
                                                                 prefix + tag.getWithEndWhitespace(),
                                                                 continuationPrefix,
                                                                 getIsMarkdown()));
      }
    }
    if (!isNull(myVersion)) {
      JDTag tag = JDTag.VERSION;
      sb.append(myFormatter.getParser().formatJDTagDescription(myVersion,
                                                               prefix + tag.getWithEndWhitespace(),
                                                               continuationPrefix,
                                                               getIsMarkdown()));
    }
  }

  public void addAuthor(@NotNull String author) {
    if (myAuthorsList == null) {
      myAuthorsList = new ArrayList<>();
    }
    myAuthorsList.add(author);
  }

  public @Nullable String getVersion() {
    return myVersion;
  }

  public void setVersion(@NotNull String version) {
    this.myVersion = version;
  }
}
