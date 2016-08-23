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

package com.intellij.injected.editor;

import gnu.trove.THashMap;

import java.util.Collection;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;

/**
 * @author cdr
*/
class ListenerWrapperMap<T extends EventListener> {
  Map<T,T> myListener2WrapperMap = new THashMap<>();

  void registerWrapper(T listener, T wrapper) {
    myListener2WrapperMap.put(listener, wrapper);
  }
  T removeWrapper(T listener) {
    return myListener2WrapperMap.remove(listener);
  }

  public Collection<T> wrappers() {
    return myListener2WrapperMap.values();
  }

  public String toString() {
    return new HashMap<>(myListener2WrapperMap).toString();
  }

  public void clear() {
    myListener2WrapperMap.clear();
  }
}
