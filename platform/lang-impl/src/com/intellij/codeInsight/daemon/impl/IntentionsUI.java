// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public abstract class IntentionsUI {
  private final Project myProject;
  public static IntentionsUI getInstance(Project project) {
    return project.getService(IntentionsUI.class);
  }

  IntentionsUI(@NotNull Project project) {
    myProject = project;
  }

  private final AtomicReference<CachedIntentions> myCachedIntentions = new AtomicReference<>();

  @NotNull
  public CachedIntentions getCachedIntentions(@NotNull Editor editor, @NotNull PsiFile file) {
    return myCachedIntentions.updateAndGet(cachedIntentions -> {
      if (cachedIntentions != null && editor == cachedIntentions.getEditor() && file == cachedIntentions.getFile()) {
        return cachedIntentions;
      }
      return new CachedIntentions(myProject, file, editor);
    });
  }

  public void invalidate() {
    myCachedIntentions.set(null);
    hide();
  }

  public void invalidateForEditor(@NotNull Editor editor) {
    myCachedIntentions.updateAndGet(
      cachedIntentions -> cachedIntentions != null && editor == cachedIntentions.getEditor() ? null : cachedIntentions);
    hideForEditor(editor);
  }

  public abstract void update(@NotNull CachedIntentions cachedIntentions, boolean actionsChanged);

  public abstract void hide();

  /**
   * Hide intention UI for a particular editor
   * @param editor editor where intention UI might be shown
   */
  public void hideForEditor(@NotNull Editor editor) {
    hide();
  }
}
