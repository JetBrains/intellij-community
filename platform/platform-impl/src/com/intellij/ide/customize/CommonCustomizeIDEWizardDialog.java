// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import java.awt.event.ActionListener;

/**
 * Allows customization of initial wizard dialog.
 * Must have a constructor with one param: @Nullable StartupUtil.AppStarter appStarter
 */
public interface CommonCustomizeIDEWizardDialog extends ActionListener {
  void show();

  boolean showIfNeeded();

  void doCancelAction();
}
