package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.library.JpsLibraryType;

/**
 * @author nik
 */
public abstract class JpsLibraryPropertiesLoader<P extends JpsElementProperties> extends JpsElementPropertiesLoader<P, JpsLibraryType<P>> {
  public JpsLibraryPropertiesLoader(JpsLibraryType<P> type, String typeId) {
    super(type, typeId);
  }

  public abstract P loadProperties(@Nullable Element propertiesElement);
}
