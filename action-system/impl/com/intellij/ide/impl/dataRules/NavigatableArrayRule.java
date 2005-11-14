package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.pom.Navigatable;

public class NavigatableArrayRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    final Navigatable element = (Navigatable)dataProvider.getData(DataConstants.NAVIGATABLE);
    if (element == null) return null;
    return new Navigatable[]{element};
  }
}
