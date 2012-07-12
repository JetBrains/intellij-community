package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.JpsSdkProperties;
import org.jetbrains.jps.model.library.JpsSdkType;

/**
 * @author nik
 */
public abstract class JpsSdkPropertiesLoader<P extends JpsSdkProperties> extends JpsElementPropertiesLoader<P, JpsSdkType<P>> {

  protected JpsSdkPropertiesLoader(String typeId, JpsSdkType<P> type) {
    super(type, typeId);
  }

  public abstract P loadProperties(String homePath, String version, @Nullable Element propertiesElement);
}
