package com.intellij.psi.jsp;

/**
 * @author ven
 */
public class JspDirectiveKind {
  private JspDirectiveKind() {}

  public static final JspDirectiveKind PAGE    = new JspDirectiveKind();
  public static final JspDirectiveKind INCLUDE = new JspDirectiveKind();
  public static final JspDirectiveKind TAGLIB  = new JspDirectiveKind();
}
