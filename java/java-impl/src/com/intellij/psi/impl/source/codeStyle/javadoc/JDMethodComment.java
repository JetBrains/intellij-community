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
public class JDMethodComment extends JDComment {
  public JDMethodComment(CommentFormatter formatter) {
    super(formatter);
  }

  private String returnTag;
  private ArrayList<NameDesc> parmsList;
  private ArrayList<NameDesc> throwsList;

  private static final @NonNls String PARAM_TAG = "@param ";
  private static final @NonNls String THROWS_TAG = "@throws ";
  private static final @NonNls String EXCEPTION_TAG = "@exception ";

  /**
   * Generates parameters or exceptions
   *
   */
  private void generateList(String prefix, StringBuffer sb, ArrayList<NameDesc> list, String tag,
                            boolean align_comments,
                            int min_name_length,
                            int max_name_length,
                            boolean generate_empty_tags
                            ) {
    int max = 0;
    if (align_comments) {
      for (Object aList : list) {
        NameDesc nd = (NameDesc)aList;
        int l = nd.name.length();
        if (isNull(nd.desc) && !generate_empty_tags) continue;
        if (l > max && l <= max_name_length) max = l;
      }
    }

    max = Math.max(max, min_name_length);

    // create filler
    StringBuffer fill = new StringBuffer(prefix.length() + tag.length() + max + 1);
    fill.append(prefix);
    int k = max + 1 + tag.length();
    for (int i = 0; i < k; i++) fill.append(' ');

    for (Object aList1 : list) {
      NameDesc nd = (NameDesc)aList1;
      if (isNull(nd.desc) && !generate_empty_tags) continue;
      if (align_comments) {
        sb.append(prefix);
        sb.append(tag);
        sb.append(nd.name);

        if (nd.name.length() > max_name_length) {
          sb.append('\n');
          sb.append(myFormatter.getParser().splitIntoCLines(nd.desc, fill, true));
        }
        else {
          int len = max - nd.name.length() + 1;
          for (int j = 0; j < len; j++) {
            sb.append(' ');
          }
          sb.append(myFormatter.getParser().splitIntoCLines(nd.desc, fill, false));
        }
      }
      else {
        sb.append(myFormatter.getParser().splitIntoCLines(tag + nd.name + " " + nd.desc, prefix, true));
      }
    }
  }

  protected void generateSpecial(String prefix, @NonNls StringBuffer sb) {

    if (parmsList != null) {
      int before = sb.length();
      generateList(prefix, sb, parmsList, PARAM_TAG,
                   myFormatter.getSettings().JD_ALIGN_PARAM_COMMENTS,
                   myFormatter.getSettings().JD_MIN_PARM_NAME_LENGTH,
                   myFormatter.getSettings().JD_MAX_PARM_NAME_LENGTH,
                   myFormatter.getSettings().JD_KEEP_EMPTY_PARAMETER
      );

      int size = sb.length() - before;
      if (size > 0 && myFormatter.getSettings().JD_ADD_BLANK_AFTER_PARM_COMMENTS) {
        sb.append(prefix);
        sb.append('\n');
      }
    }

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

  public NameDesc getParameter(String name) {
    return getNameDesc(name, parmsList);
  }

  public void removeParameter(NameDesc nd) {
    if (parmsList == null) return;
    parmsList.remove(nd);
  }

  public void removeThrow(NameDesc nd) {
    if (throwsList == null) return;
    throwsList.remove(nd);
  }

  private static NameDesc getNameDesc(String name, ArrayList<NameDesc> list) {
    if (list == null) return null;
    for (Object aList : list) {
      NameDesc parameter = (NameDesc)aList;
      if (parameter.name.equals(name)) return parameter;
    }
    return null;
  }

  public ArrayList<NameDesc> getParmsList() {
    return parmsList;
  }

  public void addParameter(String name, String description) {
    if (parmsList == null) {
      parmsList = new ArrayList<NameDesc>();
    }
    parmsList.add(new NameDesc(name, description));
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

  public void setParmsList(ArrayList<NameDesc> parmsList) {
    this.parmsList = parmsList;
  }

  public void setThrowsList(ArrayList<NameDesc> throwsList) {
    this.throwsList = throwsList;
  }

}
