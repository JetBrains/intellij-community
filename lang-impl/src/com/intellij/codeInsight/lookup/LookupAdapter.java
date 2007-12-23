package com.intellij.codeInsight.lookup;

public abstract class LookupAdapter implements LookupListener{
  public void itemSelected(LookupEvent event){
  }

  public void lookupCanceled(LookupEvent event){
  }

  public void currentItemChanged(LookupEvent event){
  }
}