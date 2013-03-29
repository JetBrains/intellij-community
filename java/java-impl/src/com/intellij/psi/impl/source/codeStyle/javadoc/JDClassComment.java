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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * Class comment
 *
 * @author Dmitry Skavish
 */
public class JDClassComment extends JDParamListOwnerComment {
  public JDClassComment(CommentFormatter formatter) {
    super(formatter);
  }

  private List<String> authorsList;
  private String version;

  @Override
  protected void generateSpecial(String prefix, @NonNls StringBuffer sb) {
    super.generateSpecial(prefix, sb);
    if (authorsList != null) {
      for (String author : authorsList) {
        sb.append(prefix);
        sb.append("@author ");
        sb.append(myFormatter.getParser().splitIntoCLines(author, prefix + "        ", false));
      }
    }
    if (!StringUtil.isEmptyOrSpaces(version)) {
      sb.append(prefix);
      sb.append("@version ");
      sb.append(myFormatter.getParser().splitIntoCLines(version, prefix + "         ", false));
    }
  }

  public void addAuthor(String author) {
    if (authorsList == null) {
      authorsList = new ArrayList<String>();
    }
    authorsList.add(author);
  }

  public List<String> getAuthorsList() {
    return authorsList;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }
}