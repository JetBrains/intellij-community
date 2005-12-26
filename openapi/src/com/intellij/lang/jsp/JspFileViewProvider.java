package com.intellij.lang.jsp;

import com.intellij.lang.Language;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: Dec 12, 2005
 * Time: 3:28:22 PM
 * To change this template use File | Settings | File Templates.
 */
public interface JspFileViewProvider extends JspxFileViewProvider {
  Language getTemplateDataLanguage();
  Set<String> getKnownTaglibPrefixes();
}
