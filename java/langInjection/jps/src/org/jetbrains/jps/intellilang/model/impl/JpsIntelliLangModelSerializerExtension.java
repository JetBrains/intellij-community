// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.intellilang.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.JpsGlobalExtensionSerializer;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;

import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public final class JpsIntelliLangModelSerializerExtension extends JpsModelSerializerExtension{
  @Override
  public @NotNull List<? extends JpsGlobalExtensionSerializer> getGlobalExtensionSerializers() {
    return Collections.singletonList(new JpsIntelliLangConfigurationSerializer());
  }
}