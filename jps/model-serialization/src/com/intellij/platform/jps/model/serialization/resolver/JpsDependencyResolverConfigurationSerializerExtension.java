// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.jps.model.serialization.resolver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

import java.util.Collections;
import java.util.List;

public final class JpsDependencyResolverConfigurationSerializerExtension extends JpsModelSerializerExtension {
  private static final JpsDependencyResolverConfigurationSerializer SERIALIZER_IMPL = new JpsDependencyResolverConfigurationSerializer();

  @Override
  public @NotNull List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Collections.singletonList(SERIALIZER_IMPL);
  }
}
