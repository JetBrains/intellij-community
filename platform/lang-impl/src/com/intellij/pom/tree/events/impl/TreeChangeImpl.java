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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.ReplaceChangeInfo;
import com.intellij.pom.tree.events.TreeChange;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TreeChangeImpl implements TreeChange {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.events.impl.TreeChangeImpl");
  private final Map<ASTNode, ChangeInfo> myChanges = new THashMap<ASTNode, ChangeInfo>();
  private final List<Pair<ASTNode,Integer>> myOffsets = new ArrayList<Pair<ASTNode, Integer>>();
  private final ASTNode myParent;

  public TreeChangeImpl(ASTNode parent) {
    myParent = parent;
  }

  public void addChange(ASTNode child, @NotNull ChangeInfo changeInfo) {
    LOG.assertTrue(child.getTreeParent() == myParent);

    final ChangeInfo current = myChanges.get(child);

    if(current != null && changeInfo.getChangeType() == ChangeInfo.CONTENTS_CHANGED){
      return;
    }

    if(changeInfo.getChangeType() == ChangeInfo.REPLACE){
      final ReplaceChangeInfoImpl replaceChangeInfo = (ReplaceChangeInfoImpl)changeInfo;
      final ASTNode replaced = replaceChangeInfo.getReplaced();
      final ChangeInfo replacedInfo = myChanges.get(replaced);

      if(replacedInfo == null){
        addChangeInternal(child, changeInfo);
      }
      else{
        switch(replacedInfo.getChangeType()){
          case ChangeInfo.REPLACE:
            replaceChangeInfo.setOldLength(replacedInfo.getOldLength());
            replaceChangeInfo.setReplaced(((ReplaceChangeInfo)replacedInfo).getReplaced());
            break;
          case ChangeInfo.ADD:
            changeInfo = ChangeInfoImpl.create(ChangeInfo.ADD, replaced);
            removeChangeInternal(replaced);
            break;
        }
        addChangeInternal(child, changeInfo);
      }
      return;
    }

    if(current != null && current.getChangeType() == ChangeInfo.REMOVED){
      if(changeInfo.getChangeType() == ChangeInfo.ADD){
        if (!(child instanceof LeafElement)) {
          changeInfo = ChangeInfoImpl.create(ChangeInfo.CONTENTS_CHANGED, child);
          ((ChangeInfoImpl)changeInfo).setOldLength(current.getOldLength());
          myChanges.put(child, changeInfo);
        }
        else {
          removeChangeInternal(child);
        }

      }
      return;
    }

    // add + remove == no op
    if(current != null && current.getChangeType() == ChangeInfo.ADD){
      if(changeInfo.getChangeType() == ChangeInfo.REMOVED){
        removeChangeInternal(child);
      }
      return;
    }

    if(changeInfo.getChangeType() == ChangeInfo.REMOVED){
      if(child instanceof LeafElement){
        final CharSequence charTabIndex = child.getChars();
        if(checkLeaf(child.getTreeNext(), charTabIndex) || checkLeaf(child.getTreePrev(), charTabIndex)) return;
      }
      addChangeInternal(child, changeInfo);
      if (current != null) {
        ((ChangeInfoImpl)changeInfo).setOldLength(current.getOldLength());
      }
      return;
    }

    if(current == null){
      addChangeInternal(child, changeInfo);
    }
  }

  private void addChangeInternal(ASTNode child, ChangeInfo info){
    if(!myChanges.containsKey(child)){
      final int nodeOffset = getNodeOffset(child);
      addChangeAtOffset(child, nodeOffset);
    }
    myChanges.put(child, info);
  }
  
  private void addChangeAtOffset(final ASTNode child, final int nodeOffset) {
    int index = 0;
    for (Pair<ASTNode, Integer> pair : myOffsets) {
      if(child == pair.getFirst()) return;
      if(nodeOffset < pair.getSecond().intValue() || nodeOffset == pair.getSecond().intValue() && isAfter(pair.getFirst(), child)){
        myOffsets.add(index, new Pair<ASTNode, Integer>(child, Integer.valueOf(nodeOffset)));
        return;
      }
      index++;
    }
    myOffsets.add(new Pair<ASTNode, Integer>(child, Integer.valueOf(nodeOffset)));
  }

  private static boolean isAfter(final ASTNode what, final ASTNode afterWhat) {
    ASTNode current = afterWhat.getTreeNext();

    while(current != null){
      if(current == what) return true;
      current = current.getTreeNext();
      if(current != null && current.getTextLength() != 0) break;
    }
    return false;
  }

  private void removeChangeInternal(ASTNode child){
    myChanges.remove(child);
    final int n = myOffsets.size();
    for(int i = 0; i < n; i++){
      if(child ==  myOffsets.get(i).getFirst()){
        myOffsets.remove(i);
        break;
      }
    }
  }


  private boolean checkLeaf(final ASTNode treeNext, final CharSequence charTabIndex) {
    if(!(treeNext instanceof LeafElement)) return false;
    final ChangeInfo right = myChanges.get(treeNext);
    if(right != null && right.getChangeType() == ChangeInfo.ADD){
      if(charTabIndex == treeNext.getChars()){
        removeChangeInternal(treeNext);
        return true;
      }
    }
    return false;
  }

  @NotNull
  public TreeElement[] getAffectedChildren() {
    final TreeElement[] treeElements = new TreeElement[myChanges.size()];
    int index = 0;
    for (final Pair<ASTNode, Integer> pair : myOffsets) {
      treeElements[index++] = (TreeElement)pair.getFirst();
    }
    return treeElements;
  }

  public ChangeInfo getChangeByChild(ASTNode child) {
    return myChanges.get(child);
  }

  public int getChildOffsetInNewTree(@NotNull ASTNode child) {
    return myParent.getStartOffset() + getNewOffset(child);
  }


  public void composite(@NotNull TreeChange treeChange) {
    final TreeChangeImpl change = (TreeChangeImpl)treeChange;
    final Set<Map.Entry<ASTNode,ChangeInfo>> entries = change.myChanges.entrySet();
    for (final Map.Entry<ASTNode, ChangeInfo> entry : entries) {
      addChange(entry.getKey(), entry.getValue());
    }
  }

  public boolean isEmpty() {
    return false;
  }

  public void removeChange(ASTNode beforeEqualDepth) {
    removeChangeInternal(beforeEqualDepth);
  }

  public void add(@NotNull final TreeChange value) {
    final TreeChangeImpl impl = (TreeChangeImpl)value;
    LOG.assertTrue(impl.myParent == myParent);

    for (final Pair<ASTNode, Integer> pair : impl.myOffsets) {
      final ASTNode child = pair.getFirst();
      ChangeInfo change = impl.getChangeByChild(child);

      if (change.getChangeType() == ChangeInfo.REMOVED) {
        final ChangeInfo oldChange = getChangeByChild(child);
        if (oldChange != null) {
          switch (oldChange.getChangeType()) {
            case ChangeInfo.ADD:
              removeChangeInternal(child);
              break;
            case ChangeInfo.REPLACE:
              final ASTNode replaced = ((ReplaceChangeInfo)oldChange).getReplaced();
              removeChangeInternal(child);
              myChanges.put(replaced, ChangeInfoImpl.create(ChangeInfo.REMOVED, replaced));
              addChangeAtOffset(replaced, getOldOffset(pair.getSecond().intValue()));
              break;
            case ChangeInfo.CONTENTS_CHANGED:
              ((ChangeInfoImpl)change).setOldLength(oldChange.getOldLength());
              myChanges.put(child, change);
              break;
          }
        }
        else {
          myChanges.put(child, change);
          addChangeAtOffset(child, getOldOffset(pair.getSecond().intValue()));
        }
      }
      else if (change.getChangeType() == ChangeInfo.REPLACE) {
        ReplaceChangeInfo replaceChangeInfo = (ReplaceChangeInfo)change;
        final ASTNode replaced = replaceChangeInfo.getReplaced();
        final ChangeInfo oldChange = getChangeByChild(replaced);
        if (oldChange != null) {
          switch (oldChange.getChangeType()) {
            case ChangeInfo.ADD:
              change = ChangeInfoImpl.create(ChangeInfo.ADD, child);
              break;
            case ChangeInfo.CONTENTS_CHANGED:
              ((ChangeInfoImpl)change).setOldLength(oldChange.getOldLength());
              break;
            case ChangeInfo.REPLACE:
              final ASTNode oldReplaced = ((ReplaceChangeInfo)oldChange).getReplaced();
              ReplaceChangeInfoImpl rep = new ReplaceChangeInfoImpl(child);
              rep.setReplaced(oldReplaced);
              change = rep;

              break;
          }
          removeChangeInternal(replaced);
        }
        addChange(child, change);
      }
      else {
        addChange(child, change);
      }
    }
  }

  public int getOldLength() {
    int oldLength = ((TreeElement)myParent).getNotCachedLength();
    for (final Map.Entry<ASTNode, ChangeInfo> entry : myChanges.entrySet()) {
      final ASTNode key = entry.getKey();
      final ChangeInfo change = entry.getValue();
      final int length = ((TreeElement)key).getNotCachedLength();
      switch (change.getChangeType()) {
        case ChangeInfo.ADD:
          oldLength -= length;
          break;
        case ChangeInfo.REMOVED:
          oldLength += length;
          break;
        case ChangeInfo.REPLACE:
        case ChangeInfo.CONTENTS_CHANGED:
          oldLength += change.getOldLength() - length;
          break;
      }
    }
    return oldLength;
  }

  private static int getNewLength(ChangeInfo change, ASTNode node){
    if(change.getChangeType() == ChangeInfo.REMOVED) return 0;
    return node.getTextLength();
  }

  private int getNodeOffset(ASTNode child){
    LOG.assertTrue(child.getTreeParent() == myParent);

    int oldOffsetInParent = 0;

    // find last changed element before child
    ASTNode current = myParent.getFirstChildNode();
    // calculate not changed elements
    while(current != child) {
      if (!myChanges.containsKey(current)) {
        oldOffsetInParent += current.getTextLength();
      }
      current = current.getTreeNext();
    }

    for (Pair<ASTNode, Integer> offset : myOffsets) {
      if(offset.getSecond() > oldOffsetInParent) break;

      final ASTNode changedNode = offset.getFirst();
      final ChangeInfo change = getChangeByChild(changedNode);
      oldOffsetInParent += change.getOldLength();
    }

    return oldOffsetInParent;
  }

  private int getOldOffset(int offset){
    for (Pair<ASTNode, Integer> pair : myOffsets) {
      if(pair.getSecond() > offset) break;

      final ASTNode changedNode = pair.getFirst();
      final ChangeInfo change = getChangeByChild(changedNode);
      offset += change.getOldLength() - getNewLength(change, changedNode);
    }

    return offset;
  }

  private int getNewOffset(ASTNode node){
    ASTNode current = myParent.getFirstChildNode();
    final Iterator<Pair<ASTNode, Integer>> i = myOffsets.iterator();
    Pair<ASTNode, Integer> currentChange = i.hasNext() ? i.next() : null;
    int currentOffsetInNewTree = 0;
    int currentOldOffset = 0;

    while(current != null) {
      boolean counted = false;

      while(currentChange != null && currentOldOffset == currentChange.getSecond().intValue()){
        if(currentChange.getFirst() == node) return currentOffsetInNewTree;
        if(current == currentChange.getFirst()){
          final int textLength = current.getTextLength();
          counted = true;
          current = current.getTreeNext();
          currentOffsetInNewTree += textLength;
        }
        final ChangeInfo changeInfo = myChanges.get(currentChange.getFirst());
        currentOldOffset += changeInfo.getOldLength();
        currentChange = i.hasNext() ? i.next() : null;
      }

      if(current == null) break;

      if(!counted){
        final int textLength = current.getTextLength();
        currentOldOffset += textLength;
        current = current.getTreeNext();
        currentOffsetInNewTree += textLength;
      }
    }
    
    return currentOffsetInNewTree;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString(){
    final StringBuilder buffer = new StringBuilder();
    final Iterator<Pair<ASTNode, Integer>> iterator = myOffsets.iterator();
    while (iterator.hasNext()) {
      final Pair<ASTNode, Integer> pair = iterator.next();
      final ASTNode node = pair.getFirst();
      buffer.append("(");
      buffer.append(node.getElementType().toString());
      buffer.append(" at ").append(pair.getSecond()).append(", ");
      buffer.append(getChangeByChild(node).toString());
      buffer.append(")");
      if(iterator.hasNext()) buffer.append(", ");
    }
    return buffer.toString();
  }
}
