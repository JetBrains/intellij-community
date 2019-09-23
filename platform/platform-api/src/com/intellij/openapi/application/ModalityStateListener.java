// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import java.util.EventListener;

public interface ModalityStateListener extends EventListener {
  void beforeModalityStateChanged(boolean entering);
}
