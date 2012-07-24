package org.jetbrains.jps.model.serialization;

import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.JpsElementType;

/**
 * @author nik
 */
public abstract class JpsElementPropertiesLoader<P extends JpsElementProperties, Type extends JpsElementType<P>> {
  private final String myTypeId;
  private final Type myType;

  public JpsElementPropertiesLoader(Type type, String typeId) {
    myType = type;
    myTypeId = typeId;
  }

  public String getTypeId() {
    return myTypeId;
  }

  public Type getType() {
    return myType;
  }
}
