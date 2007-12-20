/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.io;

import com.intellij.util.containers.IntObjectCache;
import junit.framework.TestCase;

import java.util.HashSet;

public class IntObjectCacheTest extends TestCase {
  public void testResize() {
    final int SIZE = 4;
    final IntObjectCache<String> cache = new IntObjectCache<String>(SIZE);
    cache.addDeletedPairsListener(new IntObjectCache.DeletedPairsListener() {
      public void objectRemoved(int key, Object value) {
        if (cache.count() >= cache.size() ) {
          final int newSize = cache.size() * 2;
          cache.resize(newSize);
          assertEquals(newSize, cache.size());
          cache.cacheObject(key, (String)value);
        }
      }
    });

    final int count = SIZE * 2000;
    for (int i = 1; i <= count; i++) {
      cache.cacheObject(i, String.valueOf(i));
    }

    for (int i = 1; i <= count; i++) {
      assertEquals(String.valueOf(i), cache.tryKey(i));
    }
  }
  
  public void intCacheIterator2() {
      IntObjectCache<Integer> cache = new IntObjectCache<Integer>(4);
      cache.cacheObject(0, 0);
      cache.cacheObject(1, 1);
      cache.cacheObject(2, 2);
      cache.cacheObject(4, 4);
      cache.cacheObject(3, 3);
      cache.tryKey(4);
      cache.cacheObject(4, 5);
      HashSet<Object> values = new HashSet<Object>();
      for (Object obj : cache) {
        values.add(obj);
      }
      assertFalse(values.contains(0));
      assertTrue(values.contains(1));
      assertTrue(values.contains(2));
      assertTrue(values.contains(3));
      assertTrue(values.contains(5));
      assertTrue(cache.get(4).equals(5));
    }
  
}
