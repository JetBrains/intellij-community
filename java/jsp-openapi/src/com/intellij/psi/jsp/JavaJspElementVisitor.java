// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.jsp;

import com.intellij.psi.JavaElementVisitor;


public abstract class JavaJspElementVisitor extends JavaElementVisitor {

  public void visitJspFile(JspFile jspFile) {
    visitFile(jspFile);    
  }
}
