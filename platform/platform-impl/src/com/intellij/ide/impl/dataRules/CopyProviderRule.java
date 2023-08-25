// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules;

import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;

public final class CopyProviderRule implements GetDataRule {
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    final Editor editor = DataManagerImpl.validateEditor(CommonDataKeys.EDITOR.getData(dataProvider),
                                                         PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataProvider));
    if (editor instanceof EditorEx) {
      return ((EditorEx) editor).getCopyProvider();
    }
    return null;
  }
}