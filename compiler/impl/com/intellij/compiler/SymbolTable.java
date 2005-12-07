/*
 * @author: Eugene Zhuravlev
 * Date: Mar 3, 2003
 * Time: 12:34:44 PM
 */
package com.intellij.compiler;

import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.PersistentTrie;

import java.io.File;
import java.io.IOException;

public class SymbolTable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.SymbolTable");
  private final PersistentTrie myTrie;
  private static final int CACHE_SIZE = 5 * 1024 * 1024; // 5 mbytes

  public SymbolTable(File file) throws CacheCorruptedException {
    try {
      final boolean isNewFile = file.length() == 0;
      myTrie = new PersistentTrie(file, CACHE_SIZE);
      if (!isNewFile && !myTrie.isTrieComplete()) {
        try {
          throw new CacheCorruptedException(CompilerBundle.message("error.compiler.caches.corrupted"));
        }
        finally {
          myTrie.close();
        }
      }
      myTrie.setNodeCacheSize(myTrie.getNodeCacheSize() * 4);
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized boolean isDirty() {
    return myTrie.isDirty();
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
    try {
      return myTrie.addString(symbol);
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized String getSymbol(int id) throws CacheCorruptedException {
    if (id == -1) {
      return "";
    }
    try {
      return myTrie.getStringByIndex(id);
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void dispose() throws CacheCorruptedException {
    try {
      save();
      myTrie.close();
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }
}
