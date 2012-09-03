package org.jetbrains.jps.model.serialization.library;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.serialization.JpsElementPropertiesSerializer;

/**
 * @author nik
 */
public abstract class JpsSdkPropertiesSerializer<P extends JpsElement> extends JpsElementPropertiesSerializer<P, JpsSdkType<P>> {

  protected JpsSdkPropertiesSerializer(String typeId, JpsSdkType<P> type) {
    super(type, typeId);
  }

  @NotNull
  public abstract P loadProperties(@Nullable Element propertiesElement);

  public abstract void saveProperties(@NotNull P properties, @NotNull Element element);
}
