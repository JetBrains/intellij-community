/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.options;

import javax.swing.*;

public interface Configurable extends UnnamedConfigurable {
  String getDisplayName();
  Icon getIcon();
  String getHelpTopic();
}
