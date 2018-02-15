// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.openapi.components.ServiceManager;

public interface IntentionsUI {
  class SERVICE {
    public static IntentionsUI getInstance() {
      return ServiceManager.getService(IntentionsUI.class);
    }
  }

  void update(CachedIntentions cachedIntentions, boolean actionsChanged);

  void hide();
}
