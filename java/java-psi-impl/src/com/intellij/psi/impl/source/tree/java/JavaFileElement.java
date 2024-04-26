// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaFileElement extends FileElement {
  private static final Logger LOG = Logger.getInstance(JavaFileElement.class);

  public JavaFileElement(CharSequence text) {
    super(JavaParserDefinition.JAVA_FILE, text);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getElementType() == JavaElementType.CLASS) {
      PsiJavaFile file = SourceTreeToPsiMap.treeToPsiNotNull(this);
      if (file.getClasses().length == 1) {
        file.delete();
        return;
      }
    }
    super.deleteChildInternal(child);
  }

  @Override
  public @Nullable ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.PACKAGE_STATEMENT:
        return findChildByType(JavaElementType.PACKAGE_STATEMENT);

      case ChildRole.IMPORT_LIST:
        return findChildByType(JavaElementType.IMPORT_LIST);
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaElementType.PACKAGE_STATEMENT) {
      return ChildRole.PACKAGE_STATEMENT;
    }
    else if (i == JavaElementType.IMPORT_LIST) {
      return ChildRole.IMPORT_LIST;
    }
    else if (i == JavaElementType.CLASS) {
      return ChildRole.CLASS;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void replaceChildInternal(@NotNull ASTNode child, @NotNull TreeElement newElement) {
    if (newElement.getElementType() == JavaElementType.IMPORT_LIST) {
      LOG.assertTrue(child.getElementType() == JavaElementType.IMPORT_LIST);
      if (newElement.getFirstChildNode() == null) { //empty import list
        ASTNode next = child.getTreeNext();
        if (next != null && next.getElementType() == TokenType.WHITE_SPACE) {
          removeChild(next);
        }
      }
    }
    super.replaceChildInternal(child, newElement);
  }
}
