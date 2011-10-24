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

/*
 * @author: Eugene Zhuravlev
 * Date: Mar 3, 2003
 * Time: 12:34:44 PM
 */
package com.intellij.compiler;

import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class SymbolTable {
  private final PersistentStringEnumerator myTrie;

  public SymbolTable(File file) throws CacheCorruptedException {
    try {
      if (!file.exists()) {
        FileUtil.createIfDoesntExist(file);
      }
      myTrie = new PersistentStringEnumerator(file, true);
    }
    catch (PersistentEnumerator.CorruptedException e) {
      throw new CacheCorruptedException(CompilerBundle.message("error.compiler.caches.corrupted"), e);
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int getId(@NotNull String symbol) throws CacheCorruptedException {
    if (symbol.length() == 0) {
      return -1;
    }
    try {
      return myTrie.enumerate(symbol);
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e.getCause());
    }
  }

  public String getSymbol(int id) throws CacheCorruptedException {
    if (id == -1) {
      return "";
    }
    try {
      return myTrie.valueOf(id);
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e.getCause());
    }
  }

  public void dispose() throws CacheCorruptedException {
    try {
      myTrie.close(); // will call "flush()" if needed
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }
}
