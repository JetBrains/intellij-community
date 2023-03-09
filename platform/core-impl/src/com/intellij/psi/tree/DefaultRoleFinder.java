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

package com.intellij.psi.tree;

import com.intellij.lang.ASTNode;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class DefaultRoleFinder implements RoleFinder{

  private final Supplier<? extends IElementType[]> myComputable;

  public DefaultRoleFinder(IElementType... elementTypes) {
    myComputable = () -> elementTypes;
  }

  public DefaultRoleFinder(Supplier<? extends IElementType[]> computable) {
    myComputable = computable;
  }

  @Override
  public ASTNode findChild(@NotNull ASTNode parent) {
    ASTNode current = parent.getFirstChildNode();
    while(current != null){
      if (ArrayUtil.indexOfIdentity(myComputable.get(), current.getElementType()) != -1) {
        return current;
      }
      current = current.getTreeNext();
    }
    return null;
  }
}
