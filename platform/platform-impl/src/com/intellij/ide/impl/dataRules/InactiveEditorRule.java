// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import org.jetbrains.annotations.Nullable;

public class InactiveEditorRule implements GetDataRule {
  @Override
  @Nullable
  public Object getData(final DataProvider dataProvider) {
    return dataProvider.getData(CommonDataKeys.EDITOR.getName());
  }
}