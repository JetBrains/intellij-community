// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize;

import java.awt.event.ActionListener;

/**
 * @deprecated Not used anymore.
 */
@Deprecated(forRemoval = true)
public interface CommonCustomizeIDEWizardDialog extends ActionListener {
  void show();

  boolean showIfNeeded();

  void doCancelAction();
}
