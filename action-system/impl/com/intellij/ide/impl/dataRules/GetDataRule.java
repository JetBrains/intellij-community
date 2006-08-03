package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataProvider;
import org.jetbrains.annotations.Nullable;

public interface GetDataRule {
  @Nullable
  Object getData(DataProvider dataProvider);
}
