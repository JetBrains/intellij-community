// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts.Label;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public abstract class PublicFieldBasedOptionDescription extends BooleanOptionDescription {
  private static final Logger LOG = Logger.getInstance(PublicFieldBasedOptionDescription.class);
  private final String myFieldName;

  public PublicFieldBasedOptionDescription(@Label String option, String configurableId, String fieldName) {
    super(option, configurableId);
    myFieldName = fieldName;
  }

  public abstract @NotNull Object getInstance();

  protected void fireUpdated() {
  }

  @Override
  public boolean isOptionEnabled() {
    Object instance = getInstance();
    try {
      return instance.getClass().getField(myFieldName).getBoolean(instance);
    }
    catch (Exception exception) {
      LOG.error(String.format("Boolean field '%s' not found in %s", myFieldName, instance), exception);
    }
    return false;
  }

  @Override
  public void setOptionState(boolean enabled) {
    Object instance = getInstance();
    try {
      instance.getClass().getField(myFieldName).setBoolean(instance, enabled);
    }
    catch (Exception exception) {
      LOG.error(String.format("Boolean field '%s' not found in %s", myFieldName, instance), exception);
    }
    fireUpdated();
  }
}
