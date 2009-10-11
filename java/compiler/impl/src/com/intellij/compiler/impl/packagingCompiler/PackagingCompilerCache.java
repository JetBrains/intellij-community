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
    String[] urls = ArrayUtil.newStringArray(myMap.size());
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