// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class IntentionsUI {
  private final Project myProject;
  public static IntentionsUI getInstance(Project project) {
    return ServiceManager.getService(project, IntentionsUI.class);
  }

  public IntentionsUI(Project project) {
    myProject = project;
  }

  private volatile CachedIntentions myCachedIntentions = null;

  @NotNull
  public CachedIntentions getCachedIntentions(@Nullable Editor editor, @NotNull PsiFile file) {
    CachedIntentions cachedIntentions = myCachedIntentions;
    if (cachedIntentions != null && editor == cachedIntentions.getEditor() && file == cachedIntentions.getFile()) {
      return cachedIntentions;
    } else {
      CachedIntentions intentions = new CachedIntentions(myProject, file, editor);
      myCachedIntentions = intentions;
      return intentions;
    }
  }

  public void hide() {
    myCachedIntentions = null;
    doHide();
  }

  public abstract void update(@NotNull CachedIntentions cachedIntentions, boolean actionsChanged);

  protected abstract void doHide();
}
