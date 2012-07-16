package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.module.JpsModuleType;

/**
 * @author nik
 */
public abstract class JpsModulePropertiesLoader<P extends JpsElementProperties> extends JpsElementPropertiesLoader<P, JpsModuleType<P>> {
  protected JpsModulePropertiesLoader(JpsModuleType<P> type, String typeId) {
    super(type, typeId);
  }

  public abstract P loadProperties(@Nullable Element moduleRootElement);
}
