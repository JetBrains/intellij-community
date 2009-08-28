package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.Nullable;

import java.util.EventObject;

public class LookupEvent extends EventObject {

  private final Lookup myLookup;
  private final LookupElement myItem;
  private final char myCompletionChar;

  public LookupEvent(Lookup lookup, LookupElement item){
    this(lookup, item, (char)0);
  }

  public LookupEvent(Lookup lookup, LookupElement item, char completionChar){
    super(lookup);
    myLookup = lookup;
    myItem = item;
    myCompletionChar = completionChar;
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
}
