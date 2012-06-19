package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class JpsModuleComponentSerializer {

  @NotNull
  public abstract String getComponentName();

  public abstract void loadComponent(@NotNull Element component);
}
