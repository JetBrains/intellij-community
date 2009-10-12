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

package com.intellij.history.core.tree;

import com.intellij.history.core.storage.Stream;

import java.io.IOException;

// todo replace all String.length() == 0 with String.isEmpty()
public class RootEntry extends DirectoryEntry {
  public RootEntry() {
    super(-1, "");
  }

  public RootEntry(Stream s) throws IOException {
    super(s);
  }

  protected String getPathAppendedWith(String name) {
    return name;
  }

  @Override
  public Entry findEntry(String path) {
    return searchInChildren(path);
  }

  @Override
  protected DirectoryEntry copyEntry() {
    return new RootEntry();
  }
}
