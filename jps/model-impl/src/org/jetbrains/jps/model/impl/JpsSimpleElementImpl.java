package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public class JpsSimpleElementImpl<P> extends JpsElementBase<JpsSimpleElementImpl<P>> implements JpsSimpleElement<P> {
  private P myProperties;

  public JpsSimpleElementImpl(P properties) {
    myProperties = properties;
  }

  public JpsSimpleElementImpl(JpsSimpleElementImpl<P> original) {
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
  public JpsSimpleElementImpl<P> createCopy() {
    return new JpsSimpleElementImpl<P>(this);
  }

  @Override
  public void applyChanges(@NotNull JpsSimpleElementImpl<P> modified) {
    setProperties(modified.getProperties());
  }
}
