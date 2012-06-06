package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public class SimpleJpsElementImpl<P extends JpsElementProperties> extends JpsElementBase<SimpleJpsElementImpl<P>> implements SimpleJpsElement<P> {
  private P myProperties;

  public SimpleJpsElementImpl(JpsEventDispatcher eventDispatcher, P properties, JpsParentElement parent) {
    super(eventDispatcher, parent);
    myProperties = properties;
  }

  public SimpleJpsElementImpl(SimpleJpsElementImpl<P> original, JpsEventDispatcher dispatcher, JpsParentElement parent) {
    super(original, dispatcher, parent);
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
      getEventDispatcher().fireElementChanged(this);
    }
  }

  @NotNull
  @Override
  public SimpleJpsElementImpl<P> createCopy(@NotNull JpsModel model, @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    return new SimpleJpsElementImpl<P>(this, eventDispatcher, parent);
  }

  @Override
  public void applyChanges(@NotNull SimpleJpsElementImpl<P> modified) {
    setProperties(modified.getProperties());
  }
}
