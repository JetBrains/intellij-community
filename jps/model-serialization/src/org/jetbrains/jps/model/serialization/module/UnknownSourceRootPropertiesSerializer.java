// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.module;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.UnknownSourceRootType;
import org.jetbrains.jps.model.module.UnknownSourceRootTypeProperties;

@ApiStatus.Internal
public final class UnknownSourceRootPropertiesSerializer extends JpsModuleSourceRootPropertiesSerializer<UnknownSourceRootTypeProperties<?>> {
  public UnknownSourceRootPropertiesSerializer(UnknownSourceRootType type) {
    super(type, type.getUnknownTypeId());
  }

  @Override
  public UnknownSourceRootTypeProperties<Element> loadProperties(@NotNull Element sourceRootTag) {
    return new UnknownSourceRootTypeProperties<>(sourceRootTag.getParent() != null? sourceRootTag.clone() : sourceRootTag);
  }

  @Override
  public void saveProperties(@NotNull UnknownSourceRootTypeProperties<?> properties, @NotNull Element sourceRootTag) {
    Object data = properties.getPropertiesData();
    if (data instanceof Element) {
      JDOMUtil.copyMissingContent((Element)data, sourceRootTag);
    }
  }

  public static UnknownSourceRootPropertiesSerializer forType(String unknownTypeId) {
    return forType(UnknownSourceRootType.getInstance(unknownTypeId));
  }

  public static @NotNull UnknownSourceRootPropertiesSerializer forType(UnknownSourceRootType type) {
    return new UnknownSourceRootPropertiesSerializer(type);
  }
}
