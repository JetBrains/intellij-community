// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.Changeable;

/**
 * @author Konstantin Bulenkov
 */
public class RegistryBooleanOptionDescriptor extends BooleanOptionDescription implements Changeable {
  protected final String myKey;

  public RegistryBooleanOptionDescriptor(String option, String registryKey) {
    super(option, null);
    myKey = registryKey;
  }

  @Override
  public boolean isOptionEnabled() {
    return Registry.is(myKey);
  }

  @Override
  public void setOptionState(boolean enabled) {
    Registry.get(myKey).setValue(enabled);
  }

  @Override
  public boolean hasChanged() {
    return Registry.get(myKey).isChangedFromDefault();
  }
}
