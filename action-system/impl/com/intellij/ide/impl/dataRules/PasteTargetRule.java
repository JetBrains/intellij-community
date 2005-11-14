package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;

public class PasteTargetRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    Object data = dataProvider.getData(DataConstants.PSI_ELEMENT);
    return data;
  }
}
