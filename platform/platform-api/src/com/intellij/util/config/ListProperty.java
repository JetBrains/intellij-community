/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    final ArrayList<T> modifiableList;
    final List<T> list = get(container);
    if (list instanceof ArrayList) {
      modifiableList = (ArrayList<T>)list;
    }
    else {
      modifiableList = new ArrayList<T>(list);
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

  public void clearList(AbstractPropertyContainer container) {
    getModifiableList(container).clear();
  }

  public Iterator<T> getIterator(AbstractPropertyContainer container) {
    return get(container).iterator();
  }
}
