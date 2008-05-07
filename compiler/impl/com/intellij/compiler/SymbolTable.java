/*
 * @author: Eugene Zhuravlev
 * Date: Mar 3, 2003
 * Time: 12:34:44 PM
 */
package com.intellij.compiler;

import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.IntObjectCache;
import com.intellij.util.containers.ObjectIntCache;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.File;
import java.io.IOException;

public class SymbolTable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.SymbolTable");
  private final PersistentStringEnumerator myTrie;

  // both caches should have equal size
  private static final int STRING_CACHE_SIZE = 0x4000;
  private IntObjectCache<String> myIndexStringCache = new IntObjectCache<String>(STRING_CACHE_SIZE);
  private ObjectIntCache<String> myStringIndexCache = new ObjectIntCache<String>(STRING_CACHE_SIZE);

  public SymbolTable(File file) throws CacheCorruptedException {
    try {
      if (!file.exists()) {
        file.getParentFile().mkdirs();
        file.createNewFile();
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

  public synchronized int getId(String symbol) throws CacheCorruptedException {
    LOG.assertTrue(symbol != null);
    if (symbol.length() == 0) {
      return -1;
    }
    int result = myStringIndexCache.tryKey(symbol);
    if (result != Integer.MIN_VALUE) return result;

    try {
      result = myTrie.enumerate(symbol);
      myStringIndexCache.cacheObject(symbol, result);
      return result;
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized String getSymbol(int id) throws CacheCorruptedException {
    if (id == -1) {
      return "";
    }
    String result = myIndexStringCache.tryKey(id);
    if (result != null) return result;

    try {
      result = myTrie.valueOf(id);
      myIndexStringCache.cacheObject(id, result);
      return result;
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized void dispose() throws CacheCorruptedException {
    try {
      myTrie.close(); // will call "flush()" if needed
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }
}
