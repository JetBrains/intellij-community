// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.config;

import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class ListProperty<T> extends AbstractProperty<List<T>> {
  private final String myName;

  public ListProperty(@NonNls String name) {
    myName = name;
  }

  public static <T> ListProperty<T> create(@NonNls String name) {
    return new ListProperty<>(name);
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public List<T> getDefault(AbstractProperty.AbstractPropertyContainer container) {
    return Collections.emptyList();
  }

  @Override
  public List<T> copy(List<T> value) {
    return Collections.unmodifiableList(value);
  }

  public ArrayList<T> getModifiableList(AbstractPropertyContainer container) {
    final ArrayList<T> modifiableList;
    final List<T> list = get(container);
    if (list instanceof ArrayList) {
      modifiableList = (ArrayList<T>)list;
    }
    else {
      modifiableList = new ArrayList<>(list);
      set(container, modifiableList);
    }
    // remove nulls
    for (int i = modifiableList.size() - 1; i >= 0; --i) {
      if (modifiableList.get(i) == null) {
        modifiableList.remove(i);
      }
    }
    return modifiableList;
  }

  public Iterator<T> getIterator(AbstractPropertyContainer container) {
    return get(container).iterator();
  }
}
