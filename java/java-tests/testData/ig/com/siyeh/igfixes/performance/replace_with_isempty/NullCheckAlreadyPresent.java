package com.siyeh.igfixes.performance.replace_with_isempty;

public class NullCheckAlreadyPresent {

  String[] splitProperties = {"1","2"};

  public void someAction() {
    if (splitProperties[1] != null && "".<caret>equals(splitProperties[1])) {
      System.out.println("");
    }
  }
}