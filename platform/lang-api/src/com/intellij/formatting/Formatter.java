// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.openapi.components.ServiceManager;

public interface Formatter extends IndentFactory, WrapFactory, AlignmentFactory, SpacingFactory, FormattingModelFactory {
  static Formatter getInstance() {
    Formatter instance = Holder.INSTANCE;
    if (instance == null) {
      instance = ServiceManager.getService(Formatter.class);
      Holder.INSTANCE = instance;
    }
    return instance;
  }
}

class Holder {
  // NotNullLazyValue is not used here because ServiceManager.getService can return null and better to avoid any possible issues here
  volatile static Formatter INSTANCE;
}