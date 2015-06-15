/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
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
  private final List<Pair<ASTNode,Integer>> mySortedChanges = new ArrayList<Pair<ASTNode, Integer>>(); // change, oldoffset
  private final ASTNode myParent;

  @SuppressWarnings("FieldMayBeFinal")
  private static boolean ourDoChecks = ApplicationManager.getApplication().isEAP();

  public TreeChangeImpl(ASTNode parent) {
    myParent = parent;
  }

  @Override
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
          // remove/add -> changed
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
      final int nodeOffset = getNodeOldOffset(child, info);
      addChangeAtOffset(child, nodeOffset);
    }
    myChanges.put(child, info);
  }

  private static boolean ourReportedDifferentAddChangeAtOffsetOptimization = false;

  private void addChangeAtOffset(final ASTNode child, final int nodeOffset) {
    int optimizedIndex = haveNotCalculated;

    Pair<ASTNode, Integer> element = Pair.create(child, Integer.valueOf(nodeOffset));

    if (mySortedChanges.size() > 0) { // check adding at end
      Pair<ASTNode, Integer> pair = mySortedChanges.get(mySortedChanges.size() - 1);
      if (pair.getFirst() == child.getTreePrev() && pair.getSecond() <= nodeOffset) {
        optimizedIndex = mySortedChanges.size();
        if (!ourDoChecks) {
          mySortedChanges.add(element);
          return;
        }
      }
    }

    int index = 0;

    for (Pair<ASTNode, Integer> pair : mySortedChanges) {
      if(child == pair.getFirst()) return;
      if(nodeOffset < pair.getSecond().intValue() || nodeOffset == pair.getSecond().intValue() && isAfter(pair.getFirst(), child)){
        break;
      }
      index++;
    }

    int insertionIndex = optimizedIndex != haveNotCalculated ? optimizedIndex:index;

    if (insertionIndex == mySortedChanges.size()) mySortedChanges.add(element);
    else mySortedChanges.add(insertionIndex, element);

    if (optimizedIndex != haveNotCalculated && index != optimizedIndex && !ourReportedDifferentAddChangeAtOffsetOptimization) {
      ASTNode prev = child.getTreePrev();
      Pair<ASTNode, Integer> pair = mySortedChanges.get(index);
      ChangeInfo prevChange = myChanges.get(prev);
      ChangeInfo prevChange2 = myChanges.get(pair.getFirst());
      LOG.error("Failed to calculate optimized index for add change at offset: prev node:"+prev + ", prev change:" + prevChange +
                ",prev change length:" + (prevChange != null ? prevChange.getOldLength() : null) + ", prev text length:" + prev.getTextLength() +
                ",prev offset:" + mySortedChanges.get(mySortedChanges.size() - 1).getSecond() +  ", node:" + child + ", nodeOffset:" +
                nodeOffset + ", optimizedIndex:"+optimizedIndex + ", real index:" + index + ", same node:" + (pair.getFirst() == child) +
                ", at place:"+ pair.getSecond() + ", node:" +pair.getFirst() + ", change:"+prevChange2 + ", prevChange oldLength:" +
                (prevChange2 != null ? prevChange2.getOldLength():null) + ", prevchange length2:" + pair.getFirst().getTextLength() + "," +
                toString());
      ourReportedDifferentAddChangeAtOffsetOptimization = true;
    }
  }

  private static boolean isAfter(final ASTNode what, final ASTNode afterWhat) {
    ASTNode previous = afterWhat;
    ASTNode current = previous.getTreeNext();

    while(current != null){
      if(current == what) {
        // afterWhat can be replaced during reparse and in old tree it can reference 'what' so check they are in same tree
        return what.getTreePrev() == previous;
      }
      previous = current;
      current = previous.getTreeNext();
      if(current != null && current.getTextLength() != 0) break;
    }
    return false;
  }

  private void removeChangeInternal(ASTNode child){
    myChanges.remove(child);
    for(int i = 0, n = mySortedChanges.size(); i < n; i++){
      if(child ==  mySortedChanges.get(i).getFirst()){
        mySortedChanges.remove(i);
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

  @Override
  @NotNull
  public TreeElement[] getAffectedChildren() {
    final TreeElement[] treeElements = new TreeElement[myChanges.size()];
    int index = 0;
    for (final Pair<ASTNode, Integer> pair : mySortedChanges) {
      treeElements[index++] = (TreeElement)pair.getFirst();
    }
    return treeElements;
  }

  @Override
  public ChangeInfo getChangeByChild(ASTNode child) {
    return myChanges.get(child);
  }

  @Override
  public int getChildOffsetInNewTree(@NotNull ASTNode child) {
    return myParent.getStartOffset() + getNewOffset(child);
  }


  @Override
  public void composite(@NotNull TreeChange treeChange) {
    final TreeChangeImpl change = (TreeChangeImpl)treeChange;
    final Set<Map.Entry<ASTNode,ChangeInfo>> entries = change.myChanges.entrySet();
    for (final Map.Entry<ASTNode, ChangeInfo> entry : entries) {
      addChange(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public void removeChange(ASTNode beforeEqualDepth) {
    removeChangeInternal(beforeEqualDepth);
  }

  @Override
  public void add(@NotNull final TreeChange value) {
    final TreeChangeImpl impl = (TreeChangeImpl)value;
    LOG.assertTrue(impl.myParent == myParent);

    for (final Pair<ASTNode, Integer> pair : impl.mySortedChanges) {
      final ASTNode child = pair.getFirst();
      ChangeInfo change = impl.myChanges.get(child);

      if (change.getChangeType() == ChangeInfo.REMOVED) {
        final ChangeInfo oldChange = myChanges.get(child);
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
        final ChangeInfo oldChange = myChanges.get(replaced);
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

  @Override
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

  private static final int haveNotCalculated = -1;

  private static boolean ourReportedDifferentOptimizedNodeOldOffset = false;

  private int getOptimizedNodeOldOffset(ASTNode child, ChangeInfo changeInfo) {
    // we usually add / remove ranges so old offset can be tried to calculate from change with previous sibling
    ASTNode prevSibling = child.getTreePrev();
    if (prevSibling != null) {
      if (mySortedChanges.size() > 0) {
        Pair<ASTNode, Integer> pair = mySortedChanges.get(mySortedChanges.size() - 1);

        if (pair.getFirst() == prevSibling) {
          ChangeInfo prevSiblingChange = myChanges.get(prevSibling);
          if ((prevSiblingChange.getChangeType() == ChangeInfo.REMOVED &&
               changeInfo.getChangeType() == ChangeInfo.REMOVED
              ) ||
              (prevSiblingChange.getChangeType() == ChangeInfo.ADD &&
               changeInfo.getChangeType() == ChangeInfo.ADD
              )
             ) {
            int optimizedResult = pair.getSecond() + prevSiblingChange.getOldLength();
            if (ourDoChecks && !ourReportedDifferentOptimizedNodeOldOffset) {
              int oldOffset = calculateOldOffsetLinearly(child);
              if (optimizedResult != oldOffset) {
                LOG.error("Failed optimized node old offset check:"+changeInfo + ", previous:" + prevSibling + "," + prevSiblingChange);
                ourReportedDifferentOptimizedNodeOldOffset = true;
                optimizedResult = oldOffset;
              }
            }
            return optimizedResult;
          }
        }
      }
    }
    return haveNotCalculated;
  }

  private int getNodeOldOffset(ASTNode child, ChangeInfo changeInfo){
    LOG.assertTrue(child.getTreeParent() == myParent);

    int oldOffsetInParent = getOptimizedNodeOldOffset(child, changeInfo);

    if (oldOffsetInParent == haveNotCalculated) {
      oldOffsetInParent = calculateOldOffsetLinearly(child);
    }

    return oldOffsetInParent;
  }

  private int calculateOldOffsetLinearly(ASTNode child) {
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

    for (Pair<ASTNode, Integer> offset : mySortedChanges) {
      if(offset.getSecond() > oldOffsetInParent) break;

      final ChangeInfo change = myChanges.get(offset.getFirst());
      oldOffsetInParent += change.getOldLength();
    }

    return oldOffsetInParent;
  }

  private int getOldOffset(int offset){
    for (Pair<ASTNode, Integer> pair : mySortedChanges) {
      if(pair.getSecond() > offset) break;

      final ChangeInfo change = myChanges.get(pair.getFirst());
      offset += change.getOldLength() - getNewLength(change, pair.getFirst());
    }

    return offset;
  }

  private int myLastOffsetInNewTree;
  private ASTNode myLastNode;
  private static boolean ourReportedDifferentEnableGetNewOffset = false;

  private int getNewOffset(ASTNode node){
    int optimizedResult = haveNotCalculated;

    ASTNode prev = node.getTreePrev();
    if (myLastNode == prev) {
      ChangeInfo prevChangeInfo = myChanges.get(prev);
      ChangeInfo changeInfo = myChanges.get(node);

      // newoffset of removed element is the same of removed previous sibling
      if (prevChangeInfo != null &&
          changeInfo != null &&
          prevChangeInfo.getChangeType() == ChangeInfo.REMOVED &&
          changeInfo.getChangeType() == ChangeInfo.REMOVED
         ) {
        optimizedResult = myLastOffsetInNewTree;

        myLastNode = node;
        myLastOffsetInNewTree = optimizedResult;
        if (!ourDoChecks) return optimizedResult;
      }
    }

    int currentOffsetInNewTree = 0;
    try {
      ASTNode current = myParent.getFirstChildNode();
      int i = 0;
      Pair<ASTNode, Integer> currentChange = i < mySortedChanges.size() ? mySortedChanges.get(i) : null;
      int currentOldOffset = 0;

      while(current != null) {
        boolean counted = false;

        while(currentChange != null && currentOldOffset == currentChange.getSecond().intValue()){
          if(currentChange.getFirst() == node) {
            myLastNode = node;
            myLastOffsetInNewTree = currentOffsetInNewTree;
            return currentOffsetInNewTree;
          }
          if(current == currentChange.getFirst()){
            final int textLength = current.getTextLength();
            counted = true;
            current = current.getTreeNext();
            currentOffsetInNewTree += textLength;
          }
          final ChangeInfo changeInfo = myChanges.get(currentChange.getFirst());
          currentOldOffset += changeInfo.getOldLength();
          ++i;
          currentChange = i < mySortedChanges.size() ? mySortedChanges.get(i) : null;
        }

        if(current == null) break;

        if(!counted){
          final int textLength = current.getTextLength();
          currentOldOffset += textLength;
          current = current.getTreeNext();
          currentOffsetInNewTree += textLength;
        }
      }
    }
    finally {
      if (optimizedResult != haveNotCalculated &&
          optimizedResult != currentOffsetInNewTree &&
          !ourReportedDifferentEnableGetNewOffset
        ) {
        LOG.error("Failed to calculate optimized getNewOffset:"+myChanges.get(node) + "," + prev + "," + myChanges.get(prev));
        ourReportedDifferentEnableGetNewOffset = true;
        currentOffsetInNewTree = optimizedResult; // always use optimized result
      }
    }

    return currentOffsetInNewTree;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString(){
    final StringBuilder buffer = new StringBuilder();
    final Iterator<Pair<ASTNode, Integer>> iterator = mySortedChanges.iterator();
    while (iterator.hasNext()) {
      final Pair<ASTNode, Integer> pair = iterator.next();
      final ASTNode node = pair.getFirst();
      buffer.append("(");
      buffer.append(node.getElementType().toString());
      buffer.append(" at ").append(pair.getSecond()).append(", ");
      ChangeInfo child = getChangeByChild(node);
      buffer.append(child != null ? child.toString():"null");
      buffer.append(")");
      if(iterator.hasNext()) buffer.append(", ");
    }
    return buffer.toString();
  }
}
