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

package com.intellij.pom.tree.events.impl;

import com.intellij.lang.ASTNode;
import com.intellij.pom.tree.events.ReplaceChangeInfo;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class ReplaceChangeInfoImpl extends ChangeInfoImpl implements ReplaceChangeInfo {
  private ASTNode myOldNode;
  private final ASTNode myNewNode;

  public ReplaceChangeInfoImpl(@NotNull ASTNode newNode) {
    super(REPLACE, newNode);
    myNewNode = newNode;
  }

  @Override
  public ASTNode getReplaced(){
    return myOldNode;
  }

  public void setReplaced(@NotNull ASTNode replaced) {
    CharTable charTableByTree = myNewNode.getTreeParent() == null ? null : SharedImplUtil.findCharTableByTree(myNewNode);
    setOldLength(((TreeElement)replaced).getNotCachedLength());
    myOldNode = replaced;
    myOldNode.putUserData(CharTable.CHAR_TABLE_KEY, charTableByTree);
  }
}
