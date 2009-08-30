package com.intellij.debugger.ui.tree.render;

import java.util.EventListener;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface NodeRendererSettingsListener extends EventListener{
  void renderersChanged();
}
