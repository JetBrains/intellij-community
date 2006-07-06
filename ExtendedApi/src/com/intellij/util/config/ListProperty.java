package com.intellij.util.config;

import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ListProperty<T> extends AbstractProperty<List<T>> {
  private final String myName;

  public ListProperty(@NonNls String name) {
    myName = name;
  }

  public static <T> ListProperty<T> create(@NonNls String name) {
    return new ListProperty<T>(name);
  }

  public String getName() {
    return myName;
  }

  public List<T> getDefault(AbstractProperty.AbstractPropertyContainer container) {
    return Collections.emptyList();
  }

  public List<T> copy(List<T> value) {
    return Collections.unmodifiableList(value);
  }

  public ArrayList<T> getModifiableList(AbstractPropertyContainer container) {
    List<T> list = get(container);
    if (list instanceof ArrayList) return (ArrayList<T>)list;
    ArrayList<T> modifiableList = new ArrayList<T>(list);
    set(container, modifiableList);
    return modifiableList;
  }

  public void clearList(AbstractPropertyContainer container) {
    getModifiableList(container).clear();
  }

  public Iterator<T> getIterator(AbstractPropertyContainer container) {
    return get(container).iterator();
  }
}
