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
import com.intellij.history.core.storage.UnavailableContent;

import java.io.IOException;
import java.util.Arrays;


public abstract class ContentFactory {
  public static final int MAX_CONTENT_LENGTH = 1024 * 1024;

  public Content createContent(Storage s) {
    try {
      if (isTooLong()) return new UnavailableContent();
      return s.storeContent(getBytes());
    }
    catch (IOException e) {
      return new UnavailableContent();
    }
  }

  private boolean isTooLong() throws IOException {
    return getLength() > MAX_CONTENT_LENGTH;
  }

  public boolean equalsTo(Content c) {
    try {
      if (!c.isAvailable()) return false;
      if (isTooLong()) return false;

      if (getLength() != c.getBytes().length) return false;
      return Arrays.equals(getBytes(), c.getBytes());
    }
    catch (IOException e) {
      return false;
    }
  }

  protected abstract byte[] getBytes() throws IOException;

  protected abstract long getLength() throws IOException;
}
