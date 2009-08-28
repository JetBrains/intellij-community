package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.KeyedLazyInstanceEP;
import org.jetbrains.annotations.Nullable;

public interface GetDataRule {
  ExtensionPointName<KeyedLazyInstanceEP<GetDataRule>> EP_NAME = ExtensionPointName.create("com.intellij.getDataRule");

  @Nullable
  Object getData(DataProvider dataProvider);
}
