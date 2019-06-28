// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public interface CustomizeIDEWizardStepsProvider {
  void initSteps(CustomizeIDEWizardDialog wizardDialog, List<AbstractCustomizeWizardStep> steps);

  default boolean hideSkipButton() {
    return false;
  }
}