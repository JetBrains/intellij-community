// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.library;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.serialization.JpsElementPropertiesSerializer;
import org.jetbrains.jps.model.serialization.JpsPathMapper;

public abstract class JpsLibraryPropertiesSerializer<P extends JpsElement> extends JpsElementPropertiesSerializer<P, JpsLibraryType<P>> {
  public JpsLibraryPropertiesSerializer(JpsLibraryType<P> type, String typeId) {
    super(type, typeId);
  }

  /**
   * @deprecated Override {@link #loadProperties(Element, JpsPathMapper)} instead of this method.
   */
  @Deprecated
  public P loadProperties(@Nullable Element propertiesElement) {
    throw new AbstractMethodError();
  };

  public P loadProperties(@Nullable Element propertiesElement, @NotNull JpsPathMapper pathMapper) {
    return loadProperties(propertiesElement);
  }
}
