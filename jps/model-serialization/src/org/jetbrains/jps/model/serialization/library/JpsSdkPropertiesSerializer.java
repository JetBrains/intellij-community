// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.library;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.serialization.JpsElementPropertiesSerializer;

public abstract class JpsSdkPropertiesSerializer<P extends JpsElement> extends JpsElementPropertiesSerializer<P, JpsSdkType<P>> {

  protected JpsSdkPropertiesSerializer(String typeId, JpsSdkType<P> type) {
    super(type, typeId);
  }

  public abstract @NotNull P loadProperties(@Nullable Element propertiesElement);

  /**
   * @deprecated the build process doesn't save project configuration so there is no need to implement this method, it isn't called by the platform
   */
  @Deprecated
  public void saveProperties(@NotNull P properties, @NotNull Element element) {
  }
}
