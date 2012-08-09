package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.library.JpsLibraryType;

/**
 * @author nik
 */
public abstract class JpsLibraryPropertiesSerializer<P extends JpsElement> extends JpsElementPropertiesSerializer<P, JpsLibraryType<P>> {
  public JpsLibraryPropertiesSerializer(JpsLibraryType<P> type, String typeId) {
    super(type, typeId);
  }

  public abstract P loadProperties(@Nullable Element propertiesElement);

  public abstract void saveProperties(P properties, Element element);
}
