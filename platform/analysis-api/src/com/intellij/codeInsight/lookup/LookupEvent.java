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

package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.Nullable;

import java.util.EventObject;

public class LookupEvent extends EventObject {

  private final Lookup myLookup;
  private final LookupElement myItem;
  private final char myCompletionChar;
  private final boolean myCanceledExplicitly;

  public LookupEvent(Lookup lookup, boolean canceledExplicitly){
    super(lookup);
    myLookup = lookup;
    myItem = null;
    myCompletionChar = 0;
    myCanceledExplicitly = canceledExplicitly;
  }

  public LookupEvent(Lookup lookup, LookupElement item, char completionChar) {
    super(lookup);
    myLookup = lookup;
    myItem = item;
    myCompletionChar = completionChar;
    myCanceledExplicitly = false;
  }

  public Lookup getLookup(){
    return myLookup;
  }

  @Nullable("in case ENTER was pressed when no suggestions were available")
  public LookupElement getItem(){
    return myItem;
  }

  public char getCompletionChar(){
    return myCompletionChar;
  }

  public boolean isCanceledExplicitly() {
    return myCanceledExplicitly;
  }

  public static boolean isSpecialCompletionChar(char c) {
    return c == Lookup.AUTO_INSERT_SELECT_CHAR || c == Lookup.COMPLETE_STATEMENT_SELECT_CHAR ||
           c == Lookup.NORMAL_SELECT_CHAR || c == Lookup.REPLACE_SELECT_CHAR;
  }
}
