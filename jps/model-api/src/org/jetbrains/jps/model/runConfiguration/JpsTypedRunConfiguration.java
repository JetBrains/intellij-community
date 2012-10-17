package org.jetbrains.jps.model.runConfiguration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsTypedElement;

/**
 * @author nik
 */
public interface JpsTypedRunConfiguration<P extends JpsElement> extends JpsRunConfiguration, JpsTypedElement<P> {
  @NotNull
  @Override
  P getProperties();

  @Override
  JpsRunConfigurationType<P> getType();
}
