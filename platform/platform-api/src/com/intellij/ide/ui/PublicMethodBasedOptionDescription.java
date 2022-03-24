// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  private final String myGetterName;
  private final String mySetterName;
  private final Supplier<Object> instanceProducer;

  /**
   * @deprecated Use {@link #PublicMethodBasedOptionDescription(String, String, String, String, Supplier)}
   */
  @Deprecated(forRemoval = true)
  public PublicMethodBasedOptionDescription(@NlsContexts.Label String option, String configurableId, String getterName, String setterName) {
    super(option, configurableId);
    myGetterName = getterName;
    mySetterName = setterName;
    instanceProducer = null;
  }

  public PublicMethodBasedOptionDescription(@NlsContexts.Label String option,
                                            String configurableId,
                                            String getterName,
                                            String setterName,
                                            @NotNull Supplier<@NotNull Object> instanceProducer) {
    super(option, configurableId);
    myGetterName = getterName;
    mySetterName = setterName;
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
        .findVirtual(instance.getClass(), myGetterName, MethodType.methodType(boolean.class))
        .invoke(instance);
    }
    catch (Throwable exception) {
      LOG.error(String.format("Boolean getter '%s' not found in %s", myGetterName, instance), exception);
    }
    return false;
  }

  @Override
  public void setOptionState(boolean enabled) {
    Object instance = getInstance();
    try {
      MethodHandles.publicLookup()
              .findVirtual(instance.getClass(), mySetterName, MethodType.methodType(void.class, boolean.class))
        .invoke(instance, enabled);
    }
    catch (Throwable exception) {
      LOG.error(String.format("Boolean setter '%s' not found in %s", mySetterName, instance), exception);
    }
    fireUpdated();
  }
}
