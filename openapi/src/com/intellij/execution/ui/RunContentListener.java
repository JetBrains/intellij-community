/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.ui;

import java.util.EventListener;

public interface RunContentListener extends EventListener{
  void contentSelected(RunContentDescriptor descriptor);
  void contentRemoved (RunContentDescriptor descriptor);
}