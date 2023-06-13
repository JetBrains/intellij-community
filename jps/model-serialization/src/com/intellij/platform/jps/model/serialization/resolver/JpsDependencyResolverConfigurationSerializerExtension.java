// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.jps.model.serialization.resolver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

import java.util.Collections;
import java.util.List;

public class JpsDependencyResolverConfigurationSerializerExtension extends JpsModelSerializerExtension {
  private static final JpsDependencyResolverConfigurationSerializer SERIALIZER_IMPL = new JpsDependencyResolverConfigurationSerializer();

  @NotNull
  @Override
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Collections.singletonList(SERIALIZER_IMPL);
  }
}
