package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public interface JpsElementCollection<E extends JpsElement> extends JpsElement {
  List<E> getElements();

  @NotNull
  E addChild(@NotNull JpsElementCreator<E> creator);

  <X extends E> X addChild(X element);

  void removeChild(@NotNull E element);

  void removeAllChildren();

  <X extends JpsTypedElement<P>, P extends JpsElement>
  Iterable<X> getElementsOfType(@NotNull JpsElementType<P> type);
}
