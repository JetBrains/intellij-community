/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * JTree node for arrangement rule tree.
 * <p/>
 * The general idea is to provide two additional properties - {@link #getBackingCondition() backing condition} and {@link #getRow() row}
 * and encapsulate class casts.
 * 
 * @author Denis Zhdanov
 * @since 8/20/12 10:53 PM
 */
public class ArrangementTreeNode extends DefaultMutableTreeNode {

  private static final int NO_ROW = -1;

  @Nullable private final ArrangementMatchCondition myCondition;
  private                 int                       myRow;

  public ArrangementTreeNode(@Nullable ArrangementMatchCondition condition) {
    this(condition, NO_ROW);
  }

  public ArrangementTreeNode(@Nullable ArrangementMatchCondition condition, int row) {
    myCondition = condition;
    myRow = row;
  }

  @Nullable
  public ArrangementMatchCondition getBackingCondition() {
    return myCondition;
  }

  public boolean isRowSet() {
    return myRow >= 0;
  }

  public int getRow() {
    return myRow;
  }

  public void markRow(int row) {
    myRow = row;
  }

  public void resetRow() {
    myRow = NO_ROW;
  }

  @NotNull
  public ArrangementTreeNode copy() {
    // Settings are copied by-ref intentionally here.
    return new ArrangementTreeNode(myCondition, myRow);
  }

  @Nullable
  @Override
  public ArrangementTreeNode getParent() {
    return (ArrangementTreeNode)super.getParent();
  }

  @Override
  public ArrangementTreeNode getChildAt(int index) {
    return (ArrangementTreeNode)super.getChildAt(index);
  }

  @Override
  public ArrangementTreeNode getFirstChild() {
    return (ArrangementTreeNode)super.getFirstChild();
  }

  @Override
  public ArrangementTreeNode getLastChild() {
    return (ArrangementTreeNode)super.getLastChild();
  }

  @Override
  public ArrangementTreeNode getNextSibling() {
    return (ArrangementTreeNode)super.getNextSibling();
  }

  @Override
  public ArrangementTreeNode getNextNode() {
    return (ArrangementTreeNode)super.getNextNode();
  }

  @Override
  public ArrangementTreeNode getPreviousNode() {
    return (ArrangementTreeNode)super.getPreviousNode();
  }

  @Override
  public String toString() {
    return myCondition == null ? "" : myCondition.toString() + (myRow >= 0 ? ": row=" + myRow : "");
  }
}
