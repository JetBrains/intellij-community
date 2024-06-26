// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.function.Supplier;

public class PublicMethodBasedOptionDescription extends BooleanOptionDescription {
  private static final Logger LOG = Logger.getInstance(PublicMethodBasedOptionDescription.class);
  private final String getterName;
  private final String setterName;
  private final Supplier<Object> instanceProducer;

  public PublicMethodBasedOptionDescription(@NlsContexts.Label String option,
                                            String configurableId,
                                            String getterName,
                                            String setterName,
                                            @NotNull Supplier<@NotNull Object> instanceProducer) {
    super(option, configurableId);
    this.getterName = getterName;
    this.setterName = setterName;
    this.instanceProducer = instanceProducer;
  }

  public @NotNull Object getInstance() {
    return Objects.requireNonNull(instanceProducer).get();
  }

  protected void fireUpdated() {
  }

  @Override
  public boolean isOptionEnabled() {
    Object instance = getInstance();
    try {
      return (boolean)MethodHandles.publicLookup()
        .findVirtual(instance.getClass(), getterName, MethodType.methodType(boolean.class))
        .invoke(instance);
    }
    catch (Throwable exception) {
      LOG.error(String.format("Boolean getter '%s' not found in %s", getterName, instance), exception);
    }
    return false;
  }

  @Override
  public void setOptionState(boolean enabled) {
    Object instance = getInstance();
    try {
      MethodHandles.publicLookup()
              .findVirtual(instance.getClass(), setterName, MethodType.methodType(void.class, boolean.class))
        .invoke(instance, enabled);
    }
    catch (Throwable exception) {
      LOG.error(String.format("Boolean setter '%s' not found in %s", setterName, instance), exception);
    }
    fireUpdated();
  }
}
