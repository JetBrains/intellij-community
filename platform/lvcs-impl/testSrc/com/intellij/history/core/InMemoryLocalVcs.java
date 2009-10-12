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

import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Storage;

import java.io.IOException;

public class InMemoryLocalVcs extends LocalVcs {
  public InMemoryLocalVcs() {
    this(new InMemoryStorage());
  }

  public InMemoryLocalVcs(Storage s) {
    super(s);
  }

  @Override
  protected Content createContentFrom(ContentFactory f) {
    try {
      if (f == null || f.getBytes() == null) return null;
      return f.createContent(myStorage);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected boolean contentWasNotChanged(String path, ContentFactory f) {
    if (f == null) return false;
    if (getEntry(path).getContent() == null) return false;
    return super.contentWasNotChanged(path, f);
  }
}
