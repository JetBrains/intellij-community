package com.intellij.psi.jsp;

import com.intellij.psi.JavaElementVisitor;

/**
 * @author yole
 */
public abstract class JavaJspElementVisitor extends JavaElementVisitor {
  public void visitJspImplicitVariable(JspImplicitVariable variable){
    visitImplicitVariable(variable);
  }

  public void visitJspFile(JspFile jspFile) {
    visitFile(jspFile);    
  }
}
