package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.lang.ASTNode;

public class ImportStatementElement extends ImportStatementBaseElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ImportStatementElement");

  public ImportStatementElement() {
    super(IMPORT_STATEMENT);
  }


  public ASTNode findChildByRole(int role) {
    final ASTNode result = super.findChildByRole(role);
    if (result != null) return result;
    switch (role) {
      default:
        return null;
      case ChildRole.IMPORT_REFERENCE:
        return findChildByType(JAVA_CODE_REFERENCE);
    }
  }
}
