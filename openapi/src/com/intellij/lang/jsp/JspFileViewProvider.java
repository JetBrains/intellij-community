package com.intellij.lang.jsp;

import com.intellij.lang.Language;
import com.intellij.lang.jsp.jspxLike.JspxLikeTreeLanguage;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: Dec 12, 2005
 * Time: 3:28:22 PM
 * To change this template use File | Settings | File Templates.
 */
public interface JspFileViewProvider extends JspxFileViewProvider {
  Language JSPX_LIKE_TREE = new JspxLikeTreeLanguage();
  Language getTemplateDataLanguage();
  Set<String> getKnownTaglibPrefixes();
}
