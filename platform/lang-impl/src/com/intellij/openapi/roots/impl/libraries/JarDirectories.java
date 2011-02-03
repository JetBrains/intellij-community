/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.util.containers.MultiMap;

import java.util.Collection;

/**
 * @author nik
 */
public class JarDirectories {
  private MultiMap<OrderRootType, String> myDirectories = new MultiMap<OrderRootType, String>();
  private MultiMap<OrderRootType, String> myRecursivelyIncluded = new MultiMap<OrderRootType, String>();

  public void copyFrom(JarDirectories other) {
    myDirectories.clear();
    myDirectories.putAllValues(other.myDirectories);
    myRecursivelyIncluded.clear();
    myRecursivelyIncluded.putAllValues(other.myRecursivelyIncluded);
  }

  public boolean contains(OrderRootType rootType, String url) {
    return myDirectories.get(rootType).contains(url);
  }

  public boolean isRecursive(OrderRootType rootType, String url) {
    return myRecursivelyIncluded.get(rootType).contains(url);
  }

  public void add(OrderRootType rootType, String url, boolean recursively) {
    myDirectories.putValue(rootType, url);
    if (recursively) {
      myRecursivelyIncluded.putValue(rootType, url);
    }
  }

  public void remove(OrderRootType rootType, String url) {
    myDirectories.removeValue(rootType, url);
    myRecursivelyIncluded.removeValue(rootType, url);
  }

  public void clear() {
    myDirectories.clear();
    myRecursivelyIncluded.clear();
  }

  public Collection<OrderRootType> getRootTypes() {
    return myDirectories.keySet();
  }

  public Collection<String> getDirectories(OrderRootType rootType) {
    return myDirectories.get(rootType);
  }

  public Collection<? extends String> getAllDirectories() {
    return myDirectories.values();
  }

  public boolean isEmpty() {
    return myDirectories.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JarDirectories)) return false;

    JarDirectories that = (JarDirectories)o;
    return myDirectories.equals(that.myDirectories) && myRecursivelyIncluded.equals(that.myRecursivelyIncluded);
  }

  @Override
  public int hashCode() {
    return 31 * myDirectories.hashCode() + myRecursivelyIncluded.hashCode();
  }

  @Override
  public String toString() {
    return "Jar dirs: " + myDirectories.values();
  }
}
