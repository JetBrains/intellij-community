// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler;

import com.intellij.compiler.impl.TreeBasedMap;
import com.intellij.util.containers.Interner;
import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
public class TreeBasedMapTest extends TestCase {

  public void testMapSize() {
    final TreeBasedMap<String> map = new TreeBasedMap<>(Interner.createStringInterner(), '/');

    map.put("aaa/bbb/ccc", "ValueAAABBBCCC");
    map.put("aaa/bbb/ddd", "ValueAAABBBDDD");

    assertEquals(2, map.size());
    assertEquals("ValueAAABBBDDD", map.get("aaa/bbb/ddd"));
    assertEquals("ValueAAABBBCCC", map.get("aaa/bbb/ccc"));

    map.put("aaa/bbb/ddd", "AnotherValue");

    assertEquals(2, map.size());
    assertEquals("AnotherValue", map.get("aaa/bbb/ddd"));
    assertEquals("ValueAAABBBCCC", map.get("aaa/bbb/ccc"));
  }

  public void testMapAdd() {
    final TreeBasedMap<String> map = new TreeBasedMap<>(Interner.createStringInterner(), '/');
    map.put("", "ValueEmpty");

    map.put("aaa/bbb/ccc", "ValueAAABBBCCC");
    map.put("aaa/bbb/ddd", "ValueAAABBBDDD");
    map.put("aaa/bbb", "ValueAAABBB");
    map.put("aaa/bb/cc", "ValueAAABBCC");
    map.put("aaa/bb/dd", "ValueAAABBDD");
    map.put("aaa/b/c", "ValueAAABC");

    assertEquals(7, map.size());
    assertEquals("ValueEmpty", map.get(""));
    assertEquals("ValueAAABBBCCC", map.get("aaa/bbb/ccc"));
    assertEquals("ValueAAABBBDDD", map.get("aaa/bbb/ddd"));
    assertEquals("ValueAAABBB", map.get("aaa/bbb"));
    assertEquals("ValueAAABBCC", map.get("aaa/bb/cc"));
    assertEquals("ValueAAABBDD", map.get("aaa/bb/dd"));
    assertEquals("ValueAAABC", map.get("aaa/b/c"));

    map.put("/a/b/c/", "ABC");
    assertEquals("ABC", map.get("/a/b/c/"));
    assertNull(map.get("/a/b/c"));
  }

  public void testMapRemove() {
    final TreeBasedMap<String> map = new TreeBasedMap<>(Interner.createStringInterner(), '/');
    map.put("", "ValueEmpty");

    map.put("aaa/bbb/ccc", "ValueAAABBBCCC");
    map.put("aaa/bbb/ddd", "ValueAAABBBDDD");
    map.put("aaa/bbb", "ValueAAABBB");
    map.put("aaa/bb/cc", "ValueAAABBCC");
    map.put("aaa/bb/dd", "ValueAAABBDD");
    map.put("aaa/b/c", "ValueAAABC");

    assertEquals(7, map.size());

    map.remove("");
    assertEquals(6, map.size());
    assertNull(map.get(""));
    assertEquals("ValueAAABBBCCC", map.get("aaa/bbb/ccc"));
    assertEquals("ValueAAABBBDDD", map.get("aaa/bbb/ddd"));
    assertEquals("ValueAAABBB", map.get("aaa/bbb"));
    assertEquals("ValueAAABBCC", map.get("aaa/bb/cc"));
    assertEquals("ValueAAABBDD", map.get("aaa/bb/dd"));
    assertEquals("ValueAAABC", map.get("aaa/b/c"));

    map.remove("aaa/bbb");
    assertEquals(5, map.size());
    assertNull(map.get(""));
    assertEquals("ValueAAABBBCCC", map.get("aaa/bbb/ccc"));
    assertEquals("ValueAAABBBDDD", map.get("aaa/bbb/ddd"));
    assertNull(map.get("aaa/bbb"));
    assertEquals("ValueAAABBCC", map.get("aaa/bb/cc"));
    assertEquals("ValueAAABBDD", map.get("aaa/bb/dd"));
    assertEquals("ValueAAABC", map.get("aaa/b/c"));
  }

  public void testMapIterate() {
    final TreeBasedMap<String> map = new TreeBasedMap<>(Interner.createStringInterner(), '/');
    map.put("", "ValueEmpty");
    map.put("aaa/bbb/ccc", "ValueAAABBBCCC");
    map.put("aaa/bbb/ddd", "ValueAAABBBDDD");
    map.put("aaa/bbb", "ValueAAABBB");
    map.put("aaa/bb/cc", "ValueAAABBCC");
    map.put("aaa/bb/dd", "ValueAAABBDD");
    map.put("aaa/b/c", "ValueAAABC");

    final Iterator<String> iterator = map.getKeysIterator();
    Map<String, String> checkMap = new HashMap<>();

    while (iterator.hasNext()) {
      final String key = iterator.next();
      checkMap.put(key, map.get(key));
    }

    assertEquals(7, checkMap.size());

    final Set<String> checkMapKeys = checkMap.keySet();
    assertTrue(checkMapKeys.contains(""));
    assertTrue(checkMapKeys.contains("aaa/bbb/ccc"));
    assertTrue(checkMapKeys.contains("aaa/bbb/ddd"));
    assertTrue(checkMapKeys.contains("aaa/bbb"));
    assertTrue(checkMapKeys.contains("aaa/bb/cc"));
    assertTrue(checkMapKeys.contains("aaa/bb/dd"));
    assertTrue(checkMapKeys.contains("aaa/b/c"));
    assertFalse(checkMapKeys.contains("aaa/B/c"));
    assertFalse(checkMapKeys.contains("aa/b/c"));

    assertEquals("ValueEmpty", checkMap.get(""));
    assertEquals("ValueAAABBBCCC", checkMap.get("aaa/bbb/ccc"));
    assertEquals("ValueAAABBBDDD", checkMap.get("aaa/bbb/ddd"));
    assertEquals("ValueAAABBB", checkMap.get("aaa/bbb"));
    assertEquals("ValueAAABBCC", checkMap.get("aaa/bb/cc"));
    assertEquals("ValueAAABBDD", checkMap.get("aaa/bb/dd"));
    assertEquals("ValueAAABC", checkMap.get("aaa/b/c"));
  }

  public void testMapIterate1() {
    final TreeBasedMap<String> map = new TreeBasedMap<>(Interner.createStringInterner(), '/');
    map.put("/a/b/c", "ABC");
    map.put("/a/b/c/", "ABC1");

    final Iterator<String> iterator = map.getKeysIterator();
    Map<String, String> checkMap = new HashMap<>();

    while (iterator.hasNext()) {
      final String key = iterator.next();
      checkMap.put(key, map.get(key));
    }

    assertEquals(2, checkMap.size());

    final Set<String> checkMapKeys = checkMap.keySet();
    assertTrue(checkMapKeys.contains("/a/b/c"));
    assertTrue(checkMapKeys.contains("/a/b/c/"));
    assertEquals("ABC", checkMap.get("/a/b/c"));
    assertEquals("ABC1", checkMap.get("/a/b/c/"));
  }

  public void testMapIterateAfterRemoved() {
    final TreeBasedMap<String> map = new TreeBasedMap<>(Interner.createStringInterner(), '/');
    map.put("/a/b/c", "ABC");
    map.put("/a/b/c/", "ABC1");

    map.remove("/a/b/c/");

    final Iterator<String> iterator = map.getKeysIterator();
    Map<String, String> checkMap = new HashMap<>();

    while (iterator.hasNext()) {
      final String key = iterator.next();
      checkMap.put(key, map.get(key));
    }

    assertEquals(1, checkMap.size());

    final Set<String> checkMapKeys = checkMap.keySet();
    assertTrue(checkMapKeys.contains("/a/b/c"));
    assertFalse(checkMapKeys.contains("/a/b/c/"));
    assertEquals("ABC", checkMap.get("/a/b/c"));
    assertNull(checkMap.get("/a/b/c/"));
  }
}
