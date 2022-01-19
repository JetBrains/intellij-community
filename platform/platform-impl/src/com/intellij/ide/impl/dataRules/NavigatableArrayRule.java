// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NavigatableArrayRule implements GetDataRule {
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    Object[] selection = PlatformCoreDataKeys.SELECTED_ITEMS.getData(dataProvider);
    if (selection != null) {
      List<Navigatable> res = ContainerUtil.filterIsInstance(selection, Navigatable.class);
      if (!res.isEmpty()) {
        return res.toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY);
      }
    }
    Navigatable element = CommonDataKeys.NAVIGATABLE.getData(dataProvider);
    if (element == null) {
      return null;
    }

    return new Navigatable[]{element};
  }
}
