package com.intellij.lang.jsp;

import com.intellij.psi.FileViewProvider;
import com.intellij.psi.jsp.WebDirectoryElement;
import com.intellij.lang.Language;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: Dec 12, 2005
 * Time: 7:40:40 PM
 * To change this template use File | Settings | File Templates.
 */
public interface JspxFileViewProvider extends FileViewProvider {
  Language JAVA_HOLDER_METHOD_TREE_LANGUAGE = new Language("JAVA_HOLDER_METHOD_TREE", "") {};

  WebDirectoryElement getContainingWebDirectory();
  Language getTemplateDataLanguage();
}
