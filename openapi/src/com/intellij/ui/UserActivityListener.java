/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui;

import java.util.EventListener;

public interface UserActivityListener extends EventListener {
  void stateChanged();
}