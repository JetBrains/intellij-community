package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.Nullable;

import java.util.EventObject;

public class LookupEvent extends EventObject {

  private final Lookup myLookup;
  private final LookupItem myItem;
  private final char myCompletionChar;

  public LookupEvent(Lookup lookup, LookupItem item){
    this(lookup, item, (char)0);
  }

  public LookupEvent(Lookup lookup, LookupItem item, char completionChar){
    super(lookup);
    myLookup = lookup;
    myItem = item;
    myCompletionChar = completionChar;
  }

  public Lookup getLookup(){
    return myLookup;
  }

  @Nullable("in case ENTER was pressed when no suggestions were available")
  public LookupItem getItem(){
    return myItem;
  }

  public char getCompletionChar(){
    return myCompletionChar;
  }
}