package com.google.common.collect;
import com.google.common.cache.Cache;

import java.util.Collection;

class Guava {
  public static void main(Table<String, Integer, Double> table,
                          Multimap<String, Long> multimap,
                          Multiset<Byte> multiset,
                          Cache<String, Integer> cache
  ) {
    table.get(<warning descr="Suspicious call to 'Table.get()'">new Object()</warning>, 12);
    table.get("11", <warning descr="Suspicious call to 'Table.get()'">new Object()</warning>);
    table.contains(<warning descr="Suspicious call to 'Table.contains()'">new Object()</warning>, 10);
    table.contains("9", <warning descr="Suspicious call to 'Table.contains()'">new Object()</warning>);
    table.containsRow(<warning descr="Suspicious call to 'Table.containsRow()'">new Object()</warning>);
    table.containsColumn(<warning descr="Suspicious call to 'Table.containsColumn()'">new Object()</warning>);
    table.containsValue(<warning descr="Suspicious call to 'Table.containsValue()'">new Object()</warning>);
    table.remove(<warning descr="Suspicious call to 'Table.remove()'">new Object()</warning>, 8);
    table.remove("7", <warning descr="Suspicious call to 'Table.remove()'">new Object()</warning>);

    multimap.containsKey(<warning descr="Suspicious call to 'Multimap.containsKey()'">new Object()</warning>);
    multimap.containsValue(<warning descr="Suspicious call to 'Multimap.containsValue()'">new Object()</warning>);
    multimap.containsEntry(<warning descr="Suspicious call to 'Multimap.containsEntry()'">new Object()</warning>, 6L);
    multimap.containsEntry("5", <warning descr="Suspicious call to 'Multimap.containsEntry()'">new Object()</warning>);
    multimap.remove(<warning descr="Suspicious call to 'Multimap.remove()'">new Object()</warning>, 4L);
    multimap.remove("3", <warning descr="Suspicious call to 'Multimap.remove()'">new Object()</warning>);
    multimap.removeAll(<warning descr="Suspicious call to 'Multimap.removeAll()'">new Object()</warning>);

    multiset.count(<warning descr="Suspicious call to 'Multiset.count()'">new Object()</warning>);
    multiset.remove(<warning descr="Suspicious call to 'Collection.remove()'">new Object()</warning>);

    cache.getIfPresent(<warning descr="Suspicious call to 'Cache.getIfPresent()'">new Object()</warning>);
    cache.invalidate(<warning descr="Suspicious call to 'Cache.invalidate()'">new Object()</warning>);
  }
}

interface Table<R, C, V> {
  V get(Object r, Object c);
  boolean contains(Object r, Object c);
  boolean containsRow(Object r);
  boolean containsColumn(Object c);
  boolean containsValue(Object v);
  V remove(Object r, Object c);
}

interface Multimap<K, V> {
  boolean containsKey(Object k);
  boolean containsValue(Object v);
  boolean containsEntry(Object k, Object v);
  boolean remove(Object k, Object v);
  Collection<V> removeAll(Object k);
}

interface Multiset<E> extends Collection<E> {
  int count(Object o);
}