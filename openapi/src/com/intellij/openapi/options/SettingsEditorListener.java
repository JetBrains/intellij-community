/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.options;

import java.util.EventListener;

public interface SettingsEditorListener<Settings> extends EventListener {
  void stateChanged(SettingsEditor<Settings> editor);
}