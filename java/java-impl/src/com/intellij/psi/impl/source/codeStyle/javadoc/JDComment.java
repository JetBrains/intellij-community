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
 * @author max
 */

/**
 * @author Dmitry Skavish
 */
public class JDComment {
  protected CommentFormatter myFormatter;

  String description;
  protected ArrayList unknownList;
  protected ArrayList seeAlsoList;
  protected String since;
  String deprecated;

  //protected LinkedHashMap xdocTagMap = new LinkedHashMap();

  public JDComment(CommentFormatter formatter) {
    myFormatter = formatter;
  }

  protected static boolean isNull(String s) {
    return s == null || s.trim().length() == 0;
  }

  protected static boolean isNull(ArrayList l) {
    return l == null || l.size() == 0;
  }

  public String generate(String indent) {
    final String prefix;
    if (myFormatter.getSettings().JD_LEADING_ASTERISKS_ARE_ENABLED) {
      prefix = indent + " * ";
    } else {
      prefix = indent;
    }

    @NonNls StringBuffer sb = new StringBuffer();
//  sb.append("/**\n");

    int start = sb.length();

    if (!isNull(description)) {
      sb.append(myFormatter.getParser().splitIntoCLines(description, prefix));

      if (myFormatter.getSettings().JD_ADD_BLANK_AFTER_DESCRIPTION) {
        sb.append(prefix);
        sb.append('\n');
      }
    }

    generateSpecial(prefix, sb);

    if (!isNull(unknownList) && myFormatter.getSettings().JD_KEEP_INVALID_TAGS) {
      for (Object aUnknownList : unknownList) {
        String s = (String)aUnknownList;
        sb.append(myFormatter.getParser().splitIntoCLines(s, prefix));
      }
    }

    /*
    if( xdocTagMap.size() > 0 ) {
    Iterator it = xdocTagMap.values().iterator();
    while( it.hasNext() ) {
    ArrayList list = (ArrayList) it.next();
    for( int i = 0; i<list.size(); i++ ) {
    XDTag tag = (XDTag) list.get(i);
    tag.append(sb, prefix);
    if( myFormatter.getSettings().add_blank_after_xdoclet_tag ) {
    sb.append(prefix);
    sb.append('\n');
    }
    }
    }
    }*/

    if (!isNull(seeAlsoList)) {
      for (Object aSeeAlsoList : seeAlsoList) {
        String s = (String)aSeeAlsoList;
        sb.append(prefix);
        sb.append("@see ");
        sb.append(myFormatter.getParser().splitIntoCLines(s, prefix + "     ", false));
      }
    }

    if (!isNull(since)) {
      sb.append(prefix);
      sb.append("@since ");
      sb.append(myFormatter.getParser().splitIntoCLines(since, prefix + "       ", false));
    }

    if (deprecated != null) {
      sb.append(prefix);
      sb.append("@deprecated ");
      sb.append(myFormatter.getParser().splitIntoCLines(deprecated, prefix + "            ", false));
    }

    if (sb.length() == start) return null;

    // if it ends with a blank line delete that
    int nlen = sb.length() - prefix.length() - 1;
    if (sb.substring(nlen, sb.length()).equals(prefix + "\n")) {
      sb.delete(nlen, sb.length());
    }

    if( !myFormatter.getSettings().JD_DO_NOT_WRAP_ONE_LINE_COMMENTS ||
        sb.indexOf("\n") != sb.length()-1 ) {
      sb.insert(0, "/**\n");
      sb.append(indent);
    }
    else {
      sb.replace(0, prefix.length(), "/** ");
      sb.deleteCharAt(sb.length()-1);
    }
    sb.append(" */");

    return sb.toString();
  }

  protected void generateSpecial(String prefix, StringBuffer sb) {
  }

  public void addSeeAlso(String seeAlso) {
    if (seeAlsoList == null) {
      seeAlsoList = new ArrayList();
    }
    seeAlsoList.add(seeAlso);
  }

  public void addUnknownTag(String unknownTag) {
    if (unknownList == null) {
      unknownList = new ArrayList();
    }
    unknownList.add(unknownTag);
  }
/*
    public void addXDocTag( XDTag tag ) {
        getXdocTagList(tag.getNamespaceDesc()).add(tag);
    }

    public ArrayList getXdocTagList( String nsName ) {
        ArrayList list = (ArrayList) xdocTagMap.get(nsName);
        if( list == null ) {
            list = new ArrayList();
            xdocTagMap.put(nsName, list);
        }
        return list;
    }

    public ArrayList getXdocTagList( XDNamespaceDesc desc ) {
        return getXdocTagList(desc.getName());
    }
*/
  public ArrayList getSeeAlsoList() {
    return seeAlsoList;
  }

  public void setUnknownList(ArrayList unknownList) {
    this.unknownList = unknownList;
  }

  public void setSeeAlsoList(ArrayList seeAlsoList) {
    this.seeAlsoList = seeAlsoList;
  }

  public ArrayList getUnknownList() {
    return unknownList;
  }

  public String getSince() {
    return since;
  }

  public void setSince(String since) {
    this.since = since;
  }

  public String getDeprecated() {
    return deprecated;
  }

  public void setDeprecated(String deprecated) {
    this.deprecated = deprecated;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
