/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.application;

import java.util.EventListener;

public interface ModalityStateListener extends EventListener{
  void beforeModalityStateChanged();
}
