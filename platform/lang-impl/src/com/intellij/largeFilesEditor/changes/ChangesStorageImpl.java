// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.changes;

import java.util.HashMap;

public class ChangesStorageImpl implements ChangesStorage {

  final private HashMap<Long, MarkedLinkedList<LocalChange>> myLibrary = new HashMap<>();
  final private MarkedLinkedList<Change> myTimeSortedChangesList = new MarkedLinkedList<>();

  @Override
  public boolean isEmpty() {
    return myTimeSortedChangesList.isEmpty();
  }

  @Override
  public void clear() {
    myLibrary.clear();
    myTimeSortedChangesList.clear();
  }

  /**
   * Don't change anything in returned list
   */
  @Override
  public PageValidChangesList<LocalChange> getLocalChangesForPage(long pageNumber) {
    return new PageValidChangesList<>(myLibrary.get(pageNumber));
  }

  @Override
  public void addNewLocalChange(LocalChange localChange) {
    MarkedLinkedList<LocalChange> pageChanges = myLibrary.computeIfAbsent(
      localChange.getPageNumber(), pageNumber -> new MarkedLinkedList<>());
    if (!tryToMergeLocalChangeIntoLast(localChange, pageChanges, myTimeSortedChangesList)) {
      pageChanges.addToMarkedWithCuttingTailAndMoveMarkerRight(localChange);
      myTimeSortedChangesList.addToMarkedWithCuttingTailAndMoveMarkerRight(localChange);
    }
  }

  @Override
  public Change tryRegisterUndoAndGetCorrespondingChange() {
    LocalChange localChange = ((LocalChange)myTimeSortedChangesList.getMarkedElement());
    if (localChange == null) {
      return null;
    }

    myTimeSortedChangesList.moveMarkerLeft();
    myLibrary.get(localChange.getPageNumber()).moveMarkerLeft();
    return localChange;
  }

  @Override
  public Change tryRegisterRedoAndGetCorrespondingChange() {
    if (myTimeSortedChangesList.getMarkedSize() == myTimeSortedChangesList.getSize()) {
      return null;
    }

    myTimeSortedChangesList.moveMarkerRight();
    LocalChange localChange = (LocalChange)myTimeSortedChangesList.getMarkedElement();
    myLibrary.get(localChange.getPageNumber()).moveMarkerRight();
    return localChange;
  }


  private boolean tryToMergeLocalChangeIntoLast(LocalChange localChange,
                                                MarkedLinkedList<LocalChange> pageChanges,
                                                MarkedLinkedList<Change> timeSortedChangesList) {
    if (pageChanges.getMarkedSize() == 0) {
      return false;
    }

    LocalChange lastChange = pageChanges.getMarkedElement();
    if (timeSortedChangesList.getMarkedElement() != lastChange
        || localChange.getPageNumber() != lastChange.getPageNumber()) {
      return false;
    }

    if (localChange.getOldString().length() != 0) {
      return false;
    }
    else {
      CharSequence newNewString = localChange.getNewString();
      if (newNewString.length() == 1) {
        char sym1 = newNewString.charAt(newNewString.length() - 1);
        if (Character.isLetterOrDigit(sym1)) {
          if (lastChange.getOldString().length() == 0) {
            CharSequence lastNewString = lastChange.getNewString();
            char sym2 = lastNewString.charAt(lastNewString.length() - 1);
            if (Character.isLetterOrDigit(sym2)) {
              if (lastChange.getOffset() + lastNewString.length() == localChange.getOffset()) {
                int mergedOffset = lastChange.getOffset();
                CharSequence mergedOldStr = "";
                CharSequence mergedNewStr = lastNewString.toString() + newNewString.toString();
                long mergedTimeStamp = localChange.getTimeStamp();
                long mergedPageNumber = localChange.getPageNumber();
                LocalChange mergedChange = new LocalChange(mergedPageNumber,
                                                           mergedOffset, mergedOldStr, mergedNewStr, mergedTimeStamp);

                pageChanges.moveMarkerLeft();
                pageChanges.addToMarkedWithCuttingTailAndMoveMarkerRight(mergedChange);
                timeSortedChangesList.moveMarkerLeft();
                timeSortedChangesList.addToMarkedWithCuttingTailAndMoveMarkerRight(mergedChange);
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }
}
