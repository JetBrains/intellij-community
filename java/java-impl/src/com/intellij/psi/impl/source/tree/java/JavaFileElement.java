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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaFileElement extends FileElement implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.JavaFileElement");

  public JavaFileElement(CharSequence text) {
    super(JavaParserDefinition.JAVA_FILE, text);
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (before == null && first == last && first.getElementType() == ElementType.PACKAGE_STATEMENT){ //?
      anchor = getFirstChildNode();
      before = Boolean.TRUE;
    }
    return super.addInternal(first, last, anchor, before);
  }

  public void deleteChildInternal(@NotNull ASTNode child){
    if (child.getElementType() == CLASS){
      PsiJavaFile file = (PsiJavaFile)SourceTreeToPsiMap.treeElementToPsi(this);
      if (file.getClasses().length == 1){
        file.delete();
        return;
      }
    }
    super.deleteChildInternal(child);
  }

  @Nullable
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.PACKAGE_STATEMENT:
        return findChildByType(PACKAGE_STATEMENT);

      case ChildRole.IMPORT_LIST:
        return findChildByType(IMPORT_LIST);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == PACKAGE_STATEMENT) {
      return ChildRole.PACKAGE_STATEMENT;
    }
    else if (i == IMPORT_LIST) {
      return ChildRole.IMPORT_LIST;
    }
    else if (i == CLASS) {
      return ChildRole.CLASS;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  public void replaceChildInternal(@NotNull ASTNode child, @NotNull TreeElement newElement) {
    if (newElement.getElementType() == ElementType.IMPORT_LIST) {
      LOG.assertTrue(child.getElementType() == ElementType.IMPORT_LIST);
      if (newElement.getFirstChildNode() == null) { //empty import list
        ASTNode next = child.getTreeNext();
        if (next != null && next.getElementType() == WHITE_SPACE) {
          removeChild(next);
        }
      }
    }
    super.replaceChildInternal(child, newElement);
  }
}
