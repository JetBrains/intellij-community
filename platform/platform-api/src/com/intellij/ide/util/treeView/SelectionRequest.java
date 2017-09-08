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
package com.intellij.ide.util.treeView;

import org.jetbrains.annotations.NotNull;

class SelectionRequest {
  final Object[] myElements;
  final Runnable myOnDone;
  final boolean myAddToSelection;
  final boolean myCheckCurrentSelection;
  final boolean myCheckInInStructure;
  final boolean myScrollToVisible;
  final boolean myDeferred;
  final boolean myCanSmartExpand;

  SelectionRequest(@NotNull Object[] elements,
                   Runnable onDone,
                   boolean addToSelection,
                   boolean checkCurrentSelection,
                   boolean checkInInStructure,
                   boolean scrollToVisible,
                   boolean deferred,
                   boolean canSmartExpand) {
    myElements = elements;
    myOnDone = onDone;
    myAddToSelection = addToSelection;
    myCheckCurrentSelection = checkCurrentSelection;
    myCheckInInStructure = checkInInStructure;
    myScrollToVisible = scrollToVisible;
    myDeferred = deferred;
    myCanSmartExpand = canSmartExpand;
  }

  void execute(AbstractTreeUi ui) {
    if (ui != null) ui._select(myElements, myOnDone, myAddToSelection, myCheckCurrentSelection, myCheckInInStructure, myScrollToVisible, myDeferred, myCanSmartExpand, false);
  }

  void reject() {
    if (myOnDone != null) {
      myOnDone.run();
    }
  }
}
