// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

import com.intellij.openapi.extensions.ExtensionDescriptor;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@Internal
public final class ComponentConfig {
  public static final ComponentConfig[] EMPTY_ARRAY = new ComponentConfig[0];

  public final String implementationClass;
  public final String interfaceClass;
  public final String headlessImplementationClass;
  public final ExtensionDescriptor.Os os;
  public boolean loadForDefaultProject;
  public boolean overrides;
  public final @Nullable Map<String, String> options;

  public ComponentConfig(@Nullable String interfaceClass,
                         @Nullable String implementationClass,
                         @Nullable String headlessImplementationClass,
                         boolean loadForDefaultProject,
                         @Nullable ExtensionDescriptor.Os os,
                         boolean overrides,
                         @Nullable Map<String, String> options) {
    this.implementationClass = implementationClass;
    this.interfaceClass = interfaceClass;
    this.headlessImplementationClass = headlessImplementationClass;
    this.loadForDefaultProject = loadForDefaultProject;
    this.overrides = overrides;
    this.os = os;
    this.options = options;
  }

  @Override
  public String toString() {
    return "ComponentConfig{" +
           "implementationClass='" + implementationClass + '\'' +
           ", interfaceClass='" + interfaceClass + '\'' +
           ", headlessImplementationClass='" + headlessImplementationClass + '\'' +
           ", loadForDefaultProject=" + loadForDefaultProject +
           ", options=" + options +
           '}';
  }
}
