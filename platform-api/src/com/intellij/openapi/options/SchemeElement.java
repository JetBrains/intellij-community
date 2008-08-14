package com.intellij.openapi.options;


public interface SchemeElement {
  void setGroupName(final String name);


  String getKey();

  SchemeElement copy();
}
