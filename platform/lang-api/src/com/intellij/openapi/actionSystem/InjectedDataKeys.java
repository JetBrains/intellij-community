// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.actionSystem.AnActionEvent.injectedId;

public final class InjectedDataKeys {
  private InjectedDataKeys() { }

  public static final DataKey<Editor> EDITOR = injectedKey(CommonDataKeys.EDITOR);
  public static final DataKey<Caret> CARET = injectedKey(CommonDataKeys.CARET);
  public static final DataKey<VirtualFile> VIRTUAL_FILE = injectedKey(CommonDataKeys.VIRTUAL_FILE);
  public static final DataKey<PsiFile> PSI_FILE = injectedKey(CommonDataKeys.PSI_FILE);
  public static final DataKey<PsiElement> PSI_ELEMENT = injectedKey(CommonDataKeys.PSI_ELEMENT);
  public static final DataKey<Language> LANGUAGE = injectedKey(CommonDataKeys.LANGUAGE);

  private static <T> @NotNull DataKey<T> injectedKey(@NotNull DataKey<T> key) {
    return DataKey.create(injectedId(key.getName()));
  }
}