// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.changes;

public interface ChangesStorage {

  boolean isEmpty();

  void clear();

  PageValidChangesList<LocalChange> getLocalChangesForPage(long pageNumber);

  void addNewLocalChange(LocalChange localChange);


  /**
   * If possible to undo, this method remembers the last valid change as undone and returns this change.
   * If nothing to undo, this method just returns null;
   *
   * @return change to undo if possible, null if not;
   */
  Change tryRegisterUndoAndGetCorrespondingChange();

  /**
   * If possible to redo, this method remembers the last undone change as valid and returns this change.
   * If nothing to redo, this method just returns null;
   *
   * @return change to redo if possible, null if not;
   */
  Change tryRegisterRedoAndGetCorrespondingChange();
}
