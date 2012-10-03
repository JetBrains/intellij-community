package org.jetbrains.jps.model.ex;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsModel;

/**
 * @author nik
 */
public abstract class JpsElementReferenceBase<E extends JpsElementReferenceBase<E, T>, T extends JpsElement> extends JpsElementBase<E>
  implements JpsElementReference<T> {
  @Override
  public JpsElementReference<T> asExternal(@NotNull JpsModel model) {
    model.registerExternalReference(this);
    return this;
  }
}
