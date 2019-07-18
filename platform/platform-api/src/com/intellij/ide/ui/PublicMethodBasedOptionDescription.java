// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Malenkov
 */
public abstract class PublicMethodBasedOptionDescription extends BooleanOptionDescription {
  private static final Logger LOG = Logger.getInstance(PublicMethodBasedOptionDescription.class);
  private final String myGetterName;
  private final String mySetterName;

  public PublicMethodBasedOptionDescription(String option, String configurableId, String getterName, String setterName) {
    super(option, configurableId);
    myGetterName = getterName;
    mySetterName = setterName;
  }

  @NotNull
  public abstract Object getInstance();

  protected void fireUpdated() {
  }

  @Override
  public boolean isOptionEnabled() {
    Object instance = getInstance();
    try {
      return (Boolean)instance.getClass().getMethod(myGetterName).invoke(instance);
    }
    catch (Exception exception) {
      LOG.error(String.format("Boolean getter '%s' not found in %s", myGetterName, instance), exception);
    }
    return false;
  }

  @Override
  public void setOptionState(boolean enabled) {
    Object instance = getInstance();
    try {
      instance.getClass().getMethod(mySetterName, boolean.class).invoke(instance, Boolean.valueOf(enabled));
    }
    catch (Exception exception) {
      LOG.error(String.format("Boolean setter '%s' not found in %s", mySetterName, instance), exception);
    }
    fireUpdated();
  }
}
