package com.intellij.util.indexing;

import gnu.trove.TLongObjectHashMap;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 12, 2008
 */
public class ID<K, V> {
  private static final TLongObjectHashMap<ID> ourRegistry = new TLongObjectHashMap<ID>();

  private final String myName;
  private final long myUniqueId;

  public ID(String name, long uniqueId) {
    myName = name;
    myUniqueId = uniqueId;

    if (uniqueId != -1L) {
      final ID old = ourRegistry.put(uniqueId, this);
      if (old != null) {
        ourRegistry.put(uniqueId, old);
        throw new IllegalArgumentException("ID: " + uniqueId + " is not unique. It's already occupied by " + old.myName);
      }      
    }
  }

  public static <K, V> ID<K, V> create(@NonNls String name, long uniqueId) {
    return new ID<K,V>(name, uniqueId);
  }

  public int hashCode() {
    return (int)myUniqueId;
  }

  public String toString() {
    return myName;
  }

  public long getUniqueId() {
    return myUniqueId;
  }

  public static ID<?, ?> findById(long id) {
    return ourRegistry.get(id);
  }
}
