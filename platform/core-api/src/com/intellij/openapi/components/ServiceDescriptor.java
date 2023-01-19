// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.client.ClientKind;
import com.intellij.openapi.extensions.ExtensionDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes a service which is loaded on demand.
 *
 * <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_services.html">Plugin Services</a>
 */
public final class ServiceDescriptor {
  public ServiceDescriptor(String serviceInterface,
                           String serviceImplementation,
                           String testServiceImplementation,
                           String headlessImplementation,
                           boolean overrides,
                           @Nullable String configurationSchemaKey,
                           @NotNull PreloadMode preload,
                           @Nullable ClientKind client,
                           @Nullable ExtensionDescriptor.Os os) {
    this.serviceInterface = serviceInterface;
    this.serviceImplementation = serviceImplementation;
    this.testServiceImplementation = testServiceImplementation;
    this.headlessImplementation = headlessImplementation;
    this.overrides = overrides;
    this.configurationSchemaKey = configurationSchemaKey;
    this.preload = preload;
    this.client = client;
    this.os = os;
  }

  @ApiStatus.Internal
  public enum PreloadMode {
    TRUE, FALSE, AWAIT, NOT_HEADLESS, NOT_LIGHT_EDIT
  }

  @Attribute
  public final String serviceInterface;

  @Attribute
  @RequiredElement
  public final String serviceImplementation;

  @Attribute
  public final String testServiceImplementation;

  @Attribute
  public final String headlessImplementation;

  @Attribute
  public final boolean overrides;

  /**
   * Cannot be specified as part of {@link State} because to get annotation, class must be loaded, but it cannot be done for performance reasons.
   */
  @Attribute
  @Nullable
  public final String configurationSchemaKey;

  /**
   * Preload service (before component creation). Not applicable for module level.
   *
   * Loading order and thread are not guaranteed, service should be decoupled as much as possible.
   */
  @Attribute
  @ApiStatus.Internal
  public final PreloadMode preload;

  @Attribute
  public final ExtensionDescriptor.Os os;

  /**
   * States that a separate service should be created for each client matching the specified kind.
   * Applicable only for application/project level services.
   * If the client is not specified the service is considered an ordinary one that is created once per application/project.
   */
  @Attribute
  @Nullable
  public final ClientKind client;

  public String getInterface() {
    return serviceInterface == null ? getImplementation() : serviceInterface;
  }

  public @Nullable String getImplementation() {
    if (testServiceImplementation != null && ApplicationManager.getApplication().isUnitTestMode()) {
      return testServiceImplementation;
    }
    else if (headlessImplementation != null && ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return headlessImplementation;
    }
    else {
      return serviceImplementation;
    }
  }

  @Override
  public String toString() {
    return "ServiceDescriptor(" +
           "interface='" + serviceInterface + '\'' +
           ", serviceImplementation='" + serviceImplementation + '\'' +
           ", testServiceImplementation='" + testServiceImplementation + '\'' +
           ", headlessImplementation='" + headlessImplementation + '\'' +
           ", overrides=" + overrides +
           ", configurationSchemaKey='" + configurationSchemaKey + '\'' +
           ", preload=" + preload +
           ", client=" + client +
           ')';
  }
}
