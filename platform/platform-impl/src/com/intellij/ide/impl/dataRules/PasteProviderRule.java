// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl.dataRules;

import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;

public class PasteProviderRule implements GetDataRule {
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    final Editor editor = DataManagerImpl.validateEditor(CommonDataKeys.EDITOR.getData(dataProvider),
                                                         PlatformDataKeys.CONTEXT_COMPONENT.getData(dataProvider));
    if (editor instanceof EditorEx) {
      return ((EditorEx) editor).getPasteProvider();
    }
    return null;
  }
}
