// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.pom.Navigatable;

public class NavigatableArrayRule implements GetDataRule {
  @Override
  public Object getData(DataProvider dataProvider) {
    final Navigatable element = CommonDataKeys.NAVIGATABLE.getData(dataProvider);
    if (element == null) {
      return null;
    }

    return new Navigatable[]{element};
  }
}
