// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public final class ServiceDescriptor {
  @Attribute()
  public String serviceInterface;

  @Attribute()
  public String serviceImplementation;

  @Attribute
  public String testServiceImplementation;

  @Attribute
  public boolean overrides;

  /**
   * Cannot be specified as part of {@link State} because to get annotation, class must be loaded, but it cannot be done for performance reasons.
   */
  @Attribute
  @ApiStatus.Experimental
  @Nullable
  public String configurationSchemaKey;

  public String getInterface() {
    return serviceInterface != null ? serviceInterface : getImplementation();
  }

  public String getImplementation() {
    return testServiceImplementation != null && ApplicationManager.getApplication().isUnitTestMode() ? testServiceImplementation : serviceImplementation;
  }

  @Override
  public String toString() {
    return "ServiceDescriptor(interface=" + getInterface() + ", implementation=" + getImplementation() + ")";
  }
}
