/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.options;

import javax.swing.*;
import java.util.Collection;

public interface CompositeSettingsBuilder<Settings> {
  Collection<SettingsEditor<Settings>> getEditors();
  JComponent createCompoundEditor();
}