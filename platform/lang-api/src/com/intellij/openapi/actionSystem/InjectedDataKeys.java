// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;

import static com.intellij.openapi.actionSystem.AnActionEvent.injectedId;

public final class InjectedDataKeys {
  private InjectedDataKeys() { }

  public static final DataKey<Editor> EDITOR = DataKey.create(injectedId(CommonDataKeys.EDITOR.getName()));
  public static final DataKey<Caret> CARET = DataKey.create(injectedId(CommonDataKeys.CARET.getName()));
  public static final DataKey<VirtualFile> VIRTUAL_FILE = DataKey.create(injectedId(CommonDataKeys.VIRTUAL_FILE.getName()));
  public static final DataKey<VirtualFile> PSI_FILE = DataKey.create(injectedId(CommonDataKeys.PSI_FILE.getName()));
  public static final DataKey<VirtualFile> PSI_ELEMENT = DataKey.create(injectedId(CommonDataKeys.PSI_ELEMENT.getName()));
  public static final DataKey<VirtualFile> LANGUAGE = DataKey.create(injectedId(LangDataKeys.LANGUAGE.getName()));

}