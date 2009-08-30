/*
 * @author: Eugene Zhuravlev
 * Date: Mar 3, 2003
 * Time: 12:34:44 PM
 */
package com.intellij.compiler;

import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class SymbolTable {
  private final PersistentStringEnumerator myTrie;

  // both caches should have equal size
  private static final int STRING_CACHE_SIZE = 1024;

  private final SLRUCache<Integer, String> myIndexStringCache = new SLRUCache<Integer, String>(STRING_CACHE_SIZE * 2, STRING_CACHE_SIZE) {
    @NotNull
    public String createValue(Integer key) {
      try {
        return myTrie.valueOf(key.intValue());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  };
  
  private final SLRUCache<String, Integer> myStringIndexCache = new SLRUCache<String, Integer>(STRING_CACHE_SIZE * 2, STRING_CACHE_SIZE) {
    @NotNull
    public Integer createValue(String key) {
      try {
        return myTrie.enumerate(key);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  };


  public SymbolTable(File file) throws CacheCorruptedException {
    try {
      if (!file.exists()) {
        FileUtil.createIfDoesntExist(file);
      }
      myTrie = new PersistentStringEnumerator(file);
    }
    catch (PersistentStringEnumerator.CorruptedException e) {
      throw new CacheCorruptedException(CompilerBundle.message("error.compiler.caches.corrupted"), e);
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int getId(@NotNull String symbol) throws CacheCorruptedException {
    if (symbol.length() == 0) {
      return -1;
    }
    try {
      return myStringIndexCache.get(symbol);
    }
    catch (RuntimeException e) {
      if (e.getCause() instanceof IOException) {
        throw new CacheCorruptedException(e.getCause());
      }
      throw e;
    }
  }

  public synchronized String getSymbol(int id) throws CacheCorruptedException {
    if (id == -1) {
      return "";
    }
    try {
      return myIndexStringCache.get(id);
    }
    catch (RuntimeException e) {
      if (e.getCause() instanceof IOException) {
        throw new CacheCorruptedException(e.getCause());
      }
      throw e;
    }
  }

  public synchronized void dispose() throws CacheCorruptedException {
    try {
      myIndexStringCache.clear();
      myStringIndexCache.clear();
      myTrie.close(); // will call "flush()" if needed
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }
}
