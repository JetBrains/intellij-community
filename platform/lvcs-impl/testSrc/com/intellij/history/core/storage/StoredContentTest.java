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

package com.intellij.history.core.storage;

import com.intellij.history.core.InMemoryStorage;
import com.intellij.history.core.LocalVcsTestCase;
import org.junit.Test;


public class StoredContentTest extends LocalVcsTestCase {
  @Test
  public void testEqualityAndHash() {
    assertTrue(new StoredContent(null, 1).equals(new StoredContent(null, 1)));

    assertFalse(new StoredContent(null, 1).equals(null));
    assertFalse(new StoredContent(null, 1).equals(new StoredContent(null, 2)));

    assertTrue(new StoredContent(null, 1).hashCode() == new StoredContent(null, 1).hashCode());
    assertTrue(new StoredContent(null, 1).hashCode() != new StoredContent(null, 2).hashCode());
  }

  @Test
  public void testUnavailableIfExceptionOccurs() {
    Storage goodStorage = new InMemoryStorage() {
      @Override
      protected byte[] loadContentData(final int id) throws BrokenStorageException {
        return new byte[0];
      }
    };

    Storage brokenStorage = new InMemoryStorage() {
      @Override
      protected byte[] loadContentData(int id) throws BrokenStorageException {
        throw new BrokenStorageException();
      }
    };

    assertTrue(new StoredContent(goodStorage, 0).isAvailable());
    assertFalse(new StoredContent(brokenStorage, 0).isAvailable());
  }
}
