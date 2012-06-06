package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public class JpsTypedDataImpl<T extends JpsElementType> extends JpsElementBase<JpsTypedDataImpl<T>> {
  private final T myType;
  private JpsElementProperties myProperties;

  public JpsTypedDataImpl(T type, JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    super(eventDispatcher, parent);
    myType = type;
    myProperties = type.createDefaultProperties();
  }

  public JpsTypedDataImpl(JpsTypedDataImpl<T> original,
                          JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    super(original, eventDispatcher, parent);
    myType = original.getType();
    myProperties = createCopy(original.getType(), original.myProperties);
  }

  @Nullable
  public <P extends JpsElementProperties> P getProperties(JpsElementType<P> type) {
    //noinspection unchecked
    return myType.equals(type) ? (P)myProperties : null;
  }

  private static <P extends JpsElementProperties> P createCopy(final JpsElementType<P> type,
                                                               final JpsElementProperties properties) {
    //noinspection unchecked
    return type.createCopy((P)properties);
  }

  @NotNull
  public T getType() {
    return myType;
  }

  @NotNull
  @Override
  public JpsTypedDataImpl<T> createCopy(@NotNull JpsModel model, @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    return new JpsTypedDataImpl<T>(this, eventDispatcher, parent);
  }

  @NotNull
  public JpsElementProperties getProperties() {
    return myProperties;
  }

  public void setProperties(@NotNull JpsElementProperties properties) {
    if (!myProperties.equals(properties)) {
      myProperties = properties;
      getEventDispatcher().fireElementChanged(this);
    }
  }

  public void applyChanges(@NotNull JpsTypedDataImpl<T> data) {
    setProperties(data.getProperties());
  }
}
