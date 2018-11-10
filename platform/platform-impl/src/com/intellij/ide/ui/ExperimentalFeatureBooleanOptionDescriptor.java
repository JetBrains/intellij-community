// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.application.Experiments;

/**
 * @author Konstantin Bulenkov
 */
public class ExperimentalFeatureBooleanOptionDescriptor extends RegistryBooleanOptionDescriptor {

  public ExperimentalFeatureBooleanOptionDescriptor(String option, String featureId) {
    super(option, featureId);
  }

  @Override
  public boolean isOptionEnabled() {
    return Experiments.isFeatureEnabled(myKey);
  }

  @Override
  public void setOptionState(boolean enabled) {
    Experiments.setFeatureEnabled(myKey, enabled);
  }

  @Override
  public boolean hasChanged() {
    return Experiments.isChanged(myKey);
  }
}