/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import javax.swing.*;

public interface Iconable {
  int ICON_FLAG_VISIBILITY = 0x0001;
  int ICON_FLAG_READ_STATUS = 0x0002;
  int ICON_FLAG_OPEN = 0x0004;
  int ICON_FLAG_CLOSED = 0x0008;

  Icon getIcon(int flags);
}