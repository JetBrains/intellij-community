package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

public class PackagingCompilerCache extends MapCache<Long> {
  public PackagingCompilerCache(@NonNls String storePath) {
    super(storePath);
  }

  public Long read(DataInputStream stream) throws IOException {
    return stream.readLong();
  }

  public void write(Long aLong, DataOutputStream stream) throws IOException {
    stream.writeLong(aLong.longValue());
  }

  public void update(@NonNls String url, Long state){
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

  public Long getState(String url){
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
    for (final String s : myMap.keySet()) {
      urls[idx++] = s;
    }
    return urls;
  }

  public Iterator<String> getUrlsIterator() {
    if (!load()) {
      return Arrays.asList(ArrayUtil.EMPTY_STRING_ARRAY).iterator();
    }
    return myMap.keySet().iterator();
  }

}