/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

public interface ActionButtonComponent {
  int NORMAL = 0;
  int POPPED = 1;
  int PUSHED = -1;

  int getPopState();

  int getWidth();

  int getHeight();
}
