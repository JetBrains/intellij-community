/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs.ui;

import javax.swing.*;

/**
 * author: lesya
 */
public interface RefreshableOnComponent extends Refreshable {
  JComponent getComponent();
}
