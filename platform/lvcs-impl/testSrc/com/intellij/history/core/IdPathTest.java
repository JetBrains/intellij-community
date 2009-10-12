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

package com.intellij.history.core;

import org.junit.Test;

public class IdPathTest extends LocalVcsTestCase {
  @Test
  public void testAppending() {
    IdPath p = new IdPath(1, 2);
    assertEquals(new IdPath(1, 2, 3), p.appendedWith(3));
  }

  @Test
  public void testAppendingDoesNotChangeOriginal() {
    IdPath p = new IdPath(1, 2);
    p.appendedWith(3);
    assertEquals(new IdPath(1, 2), p);
  }

  @Test
  public void testId() {
    assertEquals(3, new IdPath(1, 2, 3).getId());
  }

  @Test
  public void testParent() {
    assertEquals(new IdPath(1, 2), new IdPath(1, 2, 3).getParent());
    assertEquals(new IdPath(1), new IdPath(1, 2).getParent());
    assertNull(new IdPath(1).getParent());
  }

  @Test
  public void testIsChildOrParent() {
    IdPath p = new IdPath(1, 2, 3, 4);
    assertTrue(p.isChildOrParentOf(new IdPath(1)));
    assertTrue(p.isChildOrParentOf(new IdPath(1, 2, 3)));
    assertTrue(p.isChildOrParentOf(new IdPath(1, 2, 3, 4)));
    assertTrue(p.isChildOrParentOf(new IdPath(1, 2, 3, 4, 5, 6)));

    assertFalse(p.isChildOrParentOf(new IdPath(2)));
    assertFalse(p.isChildOrParentOf(new IdPath(1, 22)));
    assertFalse(p.isChildOrParentOf(new IdPath(1, 2, 33, 4)));
    assertFalse(p.isChildOrParentOf(new IdPath(1, 2, 3, 5)));
  }

  @Test
  public void testContains() {
    IdPath p = new IdPath(1, 2);

    assertTrue(p.contains(1));
    assertTrue(p.contains(2));

    assertFalse(p.contains(3));
  }

  @Test
  public void testStartsWith() {
    assertTrue(new IdPath(1, 2, 3).startsWith(new IdPath(1, 2)));
    assertTrue(new IdPath(1, 2, 3).startsWith(new IdPath(1, 2, 3)));

    assertFalse(new IdPath(1, 2, 3).startsWith(new IdPath(1, 3)));
    assertFalse(new IdPath(1, 2, 3).startsWith(new IdPath(1, 2, 3, 4)));
  }

  @Test
  public void testEquality() {
    assertTrue(new IdPath(1, 2, 3).equals(new IdPath(1, 2, 3)));
    assertFalse(new IdPath(1, 2, 3).equals(new IdPath(1, 2)));

    assertFalse(new IdPath(1, 2, 3).equals(null));
    assertFalse(new IdPath(1, 2, 3).equals(new Object()));
  }

  @Test
  public void testToString() {
    assertEquals("1.2.3", new IdPath(1, 2, 3).toString());
  }

  @Test
  public void testRootEquals() {
    assertTrue(new IdPath(1, 2, 3).rootEquals(1));
    assertTrue(new IdPath(1).rootEquals(1));

    assertFalse(new IdPath(1, 2, 3).rootEquals(2));
  }

  @Test
  public void testWithoutRoot() {
    assertEquals(new IdPath(2, 3), new IdPath(1, 2, 3).withoutRoot());
    assertEquals(new IdPath(2), new IdPath(1, 2).withoutRoot());
  }
}
