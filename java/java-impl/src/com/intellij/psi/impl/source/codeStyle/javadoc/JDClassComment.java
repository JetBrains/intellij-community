// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  public JDClassComment(@NotNull CommentFormatter formatter) {
    super(formatter);
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
                                                                 continuationPrefix));
      }
    }
    if (!isNull(myVersion)) {
      JDTag tag = JDTag.VERSION;
      sb.append(myFormatter.getParser().formatJDTagDescription(myVersion,
                                                               prefix + tag.getWithEndWhitespace(),
                                                               continuationPrefix));
    }
  }

  public void addAuthor(@NotNull String author) {
    if (myAuthorsList == null) {
      myAuthorsList = new ArrayList<>();
    }
    myAuthorsList.add(author);
  }

  @Nullable
  public String getVersion() {
    return myVersion;
  }

  public void setVersion(@NotNull String version) {
    this.myVersion = version;
  }
}
