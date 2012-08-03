package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.module.JpsModuleType;

/**
 * @author nik
 */
public abstract class JpsModulePropertiesSerializer<P extends JpsElementProperties> extends
                                                                                    JpsElementPropertiesSerializer<P, JpsModuleType<P>> {
  protected JpsModulePropertiesSerializer(JpsModuleType<P> type, String typeId) {
    super(type, typeId);
  }

  public abstract P loadProperties(@Nullable Element moduleRootElement);
}
