package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.JpsSdkProperties;
import org.jetbrains.jps.model.library.JpsSdkType;

/**
 * @author nik
 */
public abstract class JpsSdkPropertiesLoader<P extends JpsSdkProperties> {
  private final String myTypeId;
  private final JpsSdkType<P> myType;

  protected JpsSdkPropertiesLoader(String typeId, JpsSdkType<P> type) {
    myTypeId = typeId;
    myType = type;
  }

  public abstract P loadProperties(String homePath, String version, @Nullable Element propertiesElement);

  public String getTypeId() {
    return myTypeId;
  }

  public JpsSdkType<P> getType() {
    return myType;
  }
}
