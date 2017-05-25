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

import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      myAuthorsList = ContainerUtilRt.newArrayList();
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
