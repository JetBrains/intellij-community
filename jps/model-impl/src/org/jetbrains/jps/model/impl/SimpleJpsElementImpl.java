package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public class SimpleJpsElementImpl<P extends JpsElementProperties> extends JpsElementBase<SimpleJpsElementImpl<P>> implements SimpleJpsElement<P> {
  private P myProperties;

  public SimpleJpsElementImpl(P properties) {
    myProperties = properties;
  }

  public SimpleJpsElementImpl(SimpleJpsElementImpl<P> original) {
    myProperties = original.myProperties;
  }

  @NotNull
  @Override
  public P getProperties() {
    return myProperties;
  }

  @Override
  public void setProperties(@NotNull P properties) {
    if (!myProperties.equals(properties)) {
      myProperties = properties;
      fireElementChanged();
    }
  }

  @NotNull
  @Override
  public SimpleJpsElementImpl<P> createCopy() {
    return new SimpleJpsElementImpl<P>(this);
  }

  @Override
  public void applyChanges(@NotNull SimpleJpsElementImpl<P> modified) {
    setProperties(modified.getProperties());
  }
}
