/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.colors;

import java.util.EventListener;

public interface EditorColorsListener extends EventListener {
  void globalSchemeChange(EditorColorsScheme scheme);
}
