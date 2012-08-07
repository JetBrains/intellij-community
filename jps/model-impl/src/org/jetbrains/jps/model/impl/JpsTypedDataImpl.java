package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public class JpsTypedDataImpl<T extends JpsElementType<?>> extends JpsElementBase<JpsTypedDataImpl<T>> {
  private final T myType;
  private JpsElementProperties myProperties;

  public JpsTypedDataImpl(T type, final JpsElementProperties properties) {
    myType = type;
    myProperties = properties;
  }

  public JpsTypedDataImpl(JpsTypedDataImpl<T> original) {
    myType = original.getType();
    final JpsElementType<?> type = original.getType();
    myProperties = createCopy(type, original.myProperties);
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
  public JpsTypedDataImpl<T> createCopy() {
    return new JpsTypedDataImpl<T>(this);
  }

  @NotNull
  public JpsElementProperties getProperties() {
    return myProperties;
  }

  public void setProperties(@NotNull JpsElementProperties properties) {
    if (!myProperties.equals(properties)) {
      myProperties = properties;
      fireElementChanged();
    }
  }

  public void applyChanges(@NotNull JpsTypedDataImpl<T> data) {
    setProperties(data.getProperties());
  }

  public static <T extends JpsElementType<?>> JpsTypedDataImpl<T> createTypedData(T type, final JpsElementProperties properties) {
    return new JpsTypedDataImpl<T>(type, properties);
  }
}
