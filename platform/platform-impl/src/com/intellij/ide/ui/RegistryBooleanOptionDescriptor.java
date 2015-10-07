/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.Changeable;

/**
 * @author Konstantin Bulenkov
 */
public class RegistryBooleanOptionDescriptor extends BooleanOptionDescription implements Changeable {
  private final String myKey;

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
