// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.impl;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
final class TemplateManagerUtilBase {

  static final Key<TemplateStateBase> TEMPLATE_STATE_KEY = Key.create("TEMPLATE_STATE_KEY");

  static Editor getTopLevelEditor(@NotNull Editor editor) {
    return editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
  }

  static TemplateStateBase getTemplateState(@NotNull Editor editor) {
    UserDataHolder stateHolder = getTopLevelEditor(editor);
    TemplateStateBase templateState = stateHolder.getUserData(TEMPLATE_STATE_KEY);
    if (templateState != null && templateState.isDisposed()) {
      setTemplateState(stateHolder, null);
      return null;
    }
    return templateState;
  }

  static void setTemplateState(UserDataHolder stateHolder, @Nullable TemplateStateBase value) {
    stateHolder.putUserData(TEMPLATE_STATE_KEY, value);
  }

  static TemplateStateBase clearTemplateState(@NotNull Editor editor) {
    TemplateStateBase prevState = getTemplateState(editor);
    if (prevState != null) {
      Editor stateEditor = prevState.getEditor();
      if (stateEditor != null) {
        setTemplateState(stateEditor, null);
      }
    }
    return prevState;
  }

}
