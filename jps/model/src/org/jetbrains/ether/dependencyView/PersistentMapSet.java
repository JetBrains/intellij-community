/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.ether.dependencyView;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 08.03.11
 * Time: 15:38
 * To change this template use File | Settings | File Templates.
 */
class PersistentMapSet<X> extends PersistentHashMap<DependencyContext.S, Set<X>> {
  public PersistentMapSet(File file, KeyDescriptor<DependencyContext.S> sKeyDescriptor, DataExternalizer<Set<X>> valueExternalizer)
    throws IOException {
    super(file, sKeyDescriptor, valueExternalizer);
  }

  /*
  public static <X extends RW.Writable> void write (final BufferedWriter w, final PersistentMapSet<X> m){
    RW.writeln(w, Integer.toString(m.size()));

    for (Entry<X, Object> e : m.entrySet()) {
      e.getKey().write(w);

      final Object value = e.getValue();

      if (value instanceof Collection) {
        RW.writeln(w, "many");
        RW.writeln(w, (Collection<Y>) value);
      } else {
        RW.writeln(w, "single");
        ((Y) value).write(w);
      }
    }
  }

  public static <X, Y> PersistentMapSet<X,Y> read (final BufferedReader r, final RW.Reader<X> xr, final RW.Reader<Y> yr, final CollectionConstructor<Y> cc){
    final PersistentMapSet<X, Y> result = new PersistentMapSet<X,Y>(cc);

    final int size = RW.readInt(r);

    for (int i=0; i<size; i++) {
      final X key = xr.read(r);
      final String tag = RW.readString(r);

      if (tag.equals("many")) {
        result.put(key, RW.readMany(r, yr, cc.create()));
      } else {
        result.put(key, yr.read(r));
      }
    }

    return result;
  }

  public interface CollectionConstructor<X> {
    Collection<X> create();
  }

  private final Map<K, Object> map = new HashMap<K, Object>();

  private final CollectionConstructor<V> constr;

  public PersistentMapSet(final CollectionConstructor<V> c) {
    constr = c;
  }

  public int size() {
    return map.size();
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public boolean containsKey(final Object key) {
    return map.containsKey(key);
  }

  public boolean containsValue(final Object value) {
    return map.containsValue(value);
  }

  public Object get(final Object key) {
    return map.get(key);
  }

  public Collection<V> foxyGet(final K key) {
    final Object c = get(key);

    if (c == null) {
      return null;
    }

    if (c instanceof Collection) {
      return (Collection)c;
    }

    final Collection<V> l = constr.create();

    l.add((V)c);

    return l;
  }

  public Object put(final K key, final Object value) {
    final Object c = get(key);

    if (c == null) {
      map.put(key, value);
    }
    else {
      if (c instanceof Collection) {
        if (value instanceof Collection) {
          ((Collection)c).addAll((Collection)value);
        }
        else {
          ((Collection)c).add(value);
        }
      }
      else {
        final Collection d = constr.create();

        d.add(c);

        if (value instanceof Collection) {
          d.addAll((Collection)value);
        }
        else {
          d.add(value);
        }

        map.put(key, d);
      }
    }

    return c;
  }

  public Object remove(final Object key) {
    return map.remove(key);
  }

  public void putAll(Map<? extends K, ? extends Object> m) {
    for (Entry<? extends K, ? extends Object> e : m.entrySet()) {
      remove(e.getKey());
      put(e.getKey(), e.getValue());
    }
  }

  public void clear() {
    map.clear();
  }

  public Set<K> keySet() {
    return map.keySet();
  }

  public Collection<Object> values() {
    final List l = new LinkedList();

    for (Object value : map.values()) {
      if (value instanceof Collection) {
        l.addAll((Collection)value);
      }
      else {
        l.add(value);
      }
    }

    return l;
  }

  public Collection<V> foxyValues() {
    return (Collection<V>)values();
  }

  public Set<Entry<K, Object>> entrySet() {
    return map.entrySet();
  }
  */
}
