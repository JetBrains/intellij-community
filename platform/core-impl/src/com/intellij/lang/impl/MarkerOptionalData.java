/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.lang.impl;

import com.intellij.lang.WhitespacesAndCommentsBinder;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;

import static com.intellij.lang.WhitespacesBinders.DEFAULT_LEFT_BINDER;
import static com.intellij.lang.WhitespacesBinders.DEFAULT_RIGHT_BINDER;

/**
 * @author peter
 */
final class MarkerOptionalData extends BitSet {
  private final TIntObjectHashMap<Throwable> myDebugAllocationPositions = new TIntObjectHashMap<>();
  private final TIntObjectHashMap<String> myDoneErrors = new TIntObjectHashMap<>();
  private final TIntObjectHashMap<WhitespacesAndCommentsBinder> myLeftBinders = new TIntObjectHashMap<>();
  private final TIntObjectHashMap<WhitespacesAndCommentsBinder> myRightBinders = new TIntObjectHashMap<>();
  private final TIntHashSet myCollapsed = new TIntHashSet();

  void clean(int markerId) {
    if (get(markerId)) {
      set(markerId, false);
      myLeftBinders.remove(markerId);
      myRightBinders.remove(markerId);
      myDoneErrors.remove(markerId);
      myCollapsed.remove(markerId);
      myDebugAllocationPositions.remove(markerId);
    }
  }

  void compact() {
    myLeftBinders.compact();
    myRightBinders.compact();
    myDebugAllocationPositions.compact();
    myCollapsed.compact();
    myDoneErrors.compact();
  }

  @Nullable
  String getDoneError(int markerId) {
    return myDoneErrors.get(markerId);
  }

  boolean isCollapsed(int markerId) {
    return myCollapsed.contains(markerId);
  }

  void setErrorMessage(int markerId, String message) {
    markAsHavingOptionalData(markerId);
    myDoneErrors.put(markerId, message);
  }

  void markCollapsed(int markerId) {
    markAsHavingOptionalData(markerId);
    myCollapsed.add(markerId);
  }

  private void markAsHavingOptionalData(int markerId) {
    set(markerId);
  }

  void notifyAllocated(int markerId) {
    markAsHavingOptionalData(markerId);
    myDebugAllocationPositions.put(markerId, new Throwable("Created at the following trace."));
  }

  Throwable getAllocationTrace(PsiBuilderImpl.StartMarker marker) {
    return myDebugAllocationPositions.get(marker.markerId);
  }

  WhitespacesAndCommentsBinder getBinder(int markerId, boolean right) {
    WhitespacesAndCommentsBinder binder = getBinderMap(right).get(markerId);
    return binder != null ? binder : getDefaultBinder(right);
  }

  void assignBinder(int markerId, @NotNull WhitespacesAndCommentsBinder binder, boolean right) {
    TIntObjectHashMap<WhitespacesAndCommentsBinder> map = getBinderMap(right);
    if (binder != getDefaultBinder(right)) {
      markAsHavingOptionalData(markerId);
      map.put(markerId, binder);
    }
    else {
      map.remove(markerId);
    }
  }

  private static WhitespacesAndCommentsBinder getDefaultBinder(boolean right) {
    return right ? DEFAULT_RIGHT_BINDER : DEFAULT_LEFT_BINDER;
  }

  private TIntObjectHashMap<WhitespacesAndCommentsBinder> getBinderMap(boolean right) {
    return right ? myRightBinders : myLeftBinders;
  }

}
