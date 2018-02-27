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
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.ChildRoleBase;
import org.jetbrains.annotations.NotNull;

public class ImportStaticStatementElement extends ImportStatementBaseElement {

  public ImportStaticStatementElement() {
    super(JavaElementType.IMPORT_STATIC_STATEMENT);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    final ASTNode result = super.findChildByRole(role);
    if (result != null) return result;
    switch (role) {
      default:
        return null;

      case ChildRole.IMPORT_REFERENCE:
        final ASTNode importStaticReference = findChildByType(JavaElementType.IMPORT_STATIC_REFERENCE);
        if (importStaticReference != null) {
          return importStaticReference;
        }
        else {
          return findChildByType(JavaElementType.JAVA_CODE_REFERENCE);
        }
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    final int role = super.getChildRole(child);
    if (role != ChildRoleBase.NONE) return role;
    if (child.getElementType() == JavaElementType.IMPORT_STATIC_REFERENCE) {
      return ChildRole.IMPORT_REFERENCE;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }
}
