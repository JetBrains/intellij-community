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
  private IntObjectCache<String> myIndexStringCache = new IntObjectCache<String>(0x800);
  private ObjectIntCache<String> myStringIndexCache = new ObjectIntCache<String>(0x800);

  public SymbolTable(File file) throws CacheCorruptedException {
    try {
      myTrie = new PersistentStringEnumerator(file, 0x100000);
    }
    catch (PersistentStringEnumerator.CorruptedException e) {
      throw new CacheCorruptedException(CompilerBundle.message("error.compiler.caches.corrupted"));
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized void save() throws CacheCorruptedException {
    try {
      myTrie.flush();
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
      myIndexStringCache.cacheObject(result, symbol);
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
      myStringIndexCache.cacheObject(result, id);
      return result;
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized void dispose() throws CacheCorruptedException {
    try {
      save();
      myTrie.close();
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }
}
