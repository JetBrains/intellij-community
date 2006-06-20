package com.intellij.compiler.impl;

import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.StringInterner;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;
import java.util.Iterator;

public abstract class StateCache<T> extends MapCache<T> {
  public StateCache(@NonNls String storePath, final StringInterner interner) {
    super(interner, storePath);
  }

  public void update(@NonNls String url, T state){
    if (!load()) {
      return;
    }
    if (state != null) {
      myMap.put(url, state);
      setDirty();
    }
    else {
      remove(url);
    }
  }

  public void remove(String url){
    if (!load()) {
      return;
    }
    myMap.remove(url);
    setDirty();
  }

  public T getState(String url){
    if (!load()) {
      return null;
    }
    return myMap.get(url);
  }

  public String[] getUrls() {
    if (!load()) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    String[] urls = new String[myMap.size()];
    int idx = 0;
    for(Iterator<String> iterator = myMap.getKeysIterator(); iterator.hasNext();) {
      urls[idx++] = iterator.next();
    }
    return urls;
  }

  public Iterator<String> getUrlsIterator() {
    if (!load()) {
      return Arrays.asList(ArrayUtil.EMPTY_STRING_ARRAY).iterator();
    }
    return myMap.getKeysIterator();
  }

}