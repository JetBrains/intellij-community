package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataProvider;

public interface GetDataRule {
  Object getData(DataProvider dataProvider);
}
