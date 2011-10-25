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
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.TreeChange;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ChangeInfoImpl implements ChangeInfo {
  @NonNls private static final String[] TO_STRING = {"add", "remove", "replace", "changed"};

  private final short type;
  private int myOldLength = 0;

  public static ChangeInfoImpl create(short type, @NotNull ASTNode changed){
    if (type == REPLACE) {
      throw new IllegalArgumentException("use com.intellij.pom.tree.events.impl.ReplaceChangeInfoImpl");
    }
    return new ChangeInfoImpl(type, changed);
  }

  protected ChangeInfoImpl(short type, @NotNull ASTNode changed){
    this.type = type;
    myOldLength = type != ADD ? ((TreeElement)changed).getNotCachedLength() : 0;
  }

  @Override
  public int getChangeType(){
    return type;
  }

  public String toString(){
    return TO_STRING[getChangeType()];
  }

  public void compactChange(TreeChange change){
    for (final ASTNode treeElement : change.getAffectedChildren()) {
      final ChangeInfo changeByChild = change.getChangeByChild(treeElement);
      processElementaryChange(changeByChild, treeElement);
    }
  }

  public void processElementaryChange(final ChangeInfo changeByChild, final ASTNode treeElement) {
    switch(changeByChild.getChangeType()){
      case ADD:
        myOldLength -= ((TreeElement)treeElement).getNotCachedLength();
        break;
      case REMOVED:
        myOldLength += changeByChild.getOldLength();
        break;
      case REPLACE:
        myOldLength -= ((TreeElement)treeElement).getNotCachedLength();
        myOldLength += changeByChild.getOldLength();
        break;
      case CONTENTS_CHANGED:
        myOldLength -= ((TreeElement)treeElement).getNotCachedLength();
        myOldLength += changeByChild.getOldLength();
        break;
    }
  }

  @Override
  public int getOldLength(){
    return myOldLength;
  }

  public void setOldLength(int oldTreeLength) {
    myOldLength = oldTreeLength;
  }
}
