// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Describes a service which is loaded on demand.
 *
 * <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_services.html">Plugin Services</a>
 */
public final class ServiceDescriptor {
  public enum PreloadMode {
    TRUE, FALSE, AWAIT
  }

  @Attribute
  public String serviceInterface;

  @Attribute
  @RequiredElement
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

  /**
   * Preload service (before component creation). Not applicable for module level.
   *
   * Loading order and thread are not guaranteed, service should be decoupled as much as possible.
   */
  @Attribute
  @ApiStatus.Internal
  public PreloadMode preload = ServiceDescriptor.PreloadMode.FALSE;

  public String getInterface() {
    return serviceInterface != null ? serviceInterface : getImplementation();
  }

  @Nullable
  public String getImplementation() {
    return testServiceImplementation != null && ApplicationManager.getApplication().isUnitTestMode() ? testServiceImplementation : serviceImplementation;
  }

  @Override
  public String toString() {
    return "ServiceDescriptor(interface=" + getInterface() + ", implementation=" + getImplementation() + ")";
  }
}
