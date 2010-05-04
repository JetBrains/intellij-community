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

import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;

/**
 * Method comment
 *
 * @author Dmitry Skavish
 */
public class JDMethodComment extends JDParamListOwnerComment {
  public JDMethodComment(CommentFormatter formatter) {
    super(formatter);
  }

  private String returnTag;
  private ArrayList<NameDesc> throwsList;

  private static final @NonNls String THROWS_TAG = "@throws ";
  private static final @NonNls String EXCEPTION_TAG = "@exception ";

  protected void generateSpecial(String prefix, @NonNls StringBuffer sb) {

    super.generateSpecial(prefix, sb);

    if (returnTag != null) {
      if (returnTag.trim().length() != 0 || myFormatter.getSettings().JD_KEEP_EMPTY_RETURN) {
        sb.append(prefix);
        sb.append("@return ");
        sb.append(myFormatter.getParser().splitIntoCLines(returnTag, prefix + "        ", false));
        if (myFormatter.getSettings().JD_ADD_BLANK_AFTER_RETURN) {
          sb.append(prefix);
          sb.append('\n');
        }
      }
    }

    if (throwsList != null) {
      String tag = myFormatter.getSettings().JD_USE_THROWS_NOT_EXCEPTION ? THROWS_TAG : EXCEPTION_TAG;
      generateList(prefix, sb, throwsList, tag,
                   myFormatter.getSettings().JD_ALIGN_EXCEPTION_COMMENTS,
                   myFormatter.getSettings().JD_MIN_EXCEPTION_NAME_LENGTH,
                   myFormatter.getSettings().JD_MAX_EXCEPTION_NAME_LENGTH,
                   myFormatter.getSettings().JD_KEEP_EMPTY_EXCEPTION
      );
    }
  }

  public String getReturnTag() {
    return returnTag;
  }

  public void setReturnTag(String returnTag) {
    this.returnTag = returnTag;
  }

  public void removeThrow(NameDesc nd) {
    if (throwsList == null) return;
    throwsList.remove(nd);
  }

  public ArrayList<NameDesc> getThrowsList() {
    return throwsList;
  }

  public void addThrow(String className, String description) {
    if (throwsList == null) {
      throwsList = new ArrayList<NameDesc>();
    }
    throwsList.add(new NameDesc(className, description));
  }

  public NameDesc getThrow(String name) {
    return getNameDesc(name, throwsList);
  }

  public void setThrowsList(ArrayList<NameDesc> throwsList) {
    this.throwsList = throwsList;
  }

}
