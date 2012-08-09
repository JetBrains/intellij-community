package org.jetbrains.jps.model.serialization;

/**
 * @author nik
 */
public abstract class JpsElementPropertiesSerializer<P, Type> {
  private final String myTypeId;
  private final Type myType;

  public JpsElementPropertiesSerializer(Type type, String typeId) {
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
