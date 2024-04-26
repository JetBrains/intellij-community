// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.Nullable;

import java.util.EventObject;

public final class LookupEvent extends EventObject {

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

  public @Nullable("in case ENTER was pressed when no suggestions were available") LookupElement getItem(){
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
