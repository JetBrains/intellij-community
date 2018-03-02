// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface IntentionsUI {
  static IntentionsUI getInstance(Project project) {
    return ServiceManager.getService(project, IntentionsUI.class);
  }

  void update(@NotNull CachedIntentions cachedIntentions, boolean actionsChanged);

  void hide();
}
