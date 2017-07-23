/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.model;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * The basic design of external system integration assumes that target project info if represented as a generic graph
 * of {@link DataNode} objects where every {@link DataNode} content type is identified by an instance of this class.
 * <p/>
 * That makes it possible to register custom {@link DataNode} processor per-{@link Key}
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 4/12/13 11:49 AM
 * @param <T>  data class
 */
@SuppressWarnings("UnusedDeclaration")
public class Key<T> implements Serializable, Comparable<Key<?>> {

  private static final long serialVersionUID = 1L;
  
  @NotNull private final String myDataClass;
  
  private final int myProcessingWeight;

  /**
   * Creates new {@code Key} object.
   * 
   * @param dataClass         class of the payload data which will be associated with the current key
   * @param processingWeight  there is a possible case that when a {@link DataNode} object has children of more than on type (children
   *                          with more than one different {@link Key} we might want to process one type of children before another.
   *                          That's why we need a way to define that processing order. This parameter serves exactly for that -
   *                          lower value means that key's payload should be processed <b>before</b> payload of the key with a greater
   *                          value
   */
  public Key(@NotNull String dataClass, int processingWeight) {
    myDataClass = dataClass;
    myProcessingWeight = processingWeight;
  }

  @NotNull
  public static <T> Key<T> create(@NotNull Class<T> dataClass, int processingWeight) {
    return new Key<>(dataClass.getName(), processingWeight);
  }

  public String getDataType() {
    return myDataClass;
  }

  /**
   * There is a possible case that when a {@link DataNode} object has children of more than on type (children with more than
   * one different {@link Key} we might want to process one type of children before another. That's why we need a way to define
   * that processing order. This property serves exactly for that - lower value means that key's payload should be processed
   * <b>before</b> payload of the key with a greater value.
   * 
   * @return    processing weight for data associated with the current key
   */
  public int getProcessingWeight() {
    return myProcessingWeight;
  }

  @Override
  public int hashCode() {
    return myDataClass.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Key key = (Key)o;

    if (!myDataClass.equals(key.myDataClass)) return false;

    return true;
  }

  @Override
  public int compareTo(@NotNull Key<?> that) {
    if(myProcessingWeight == that.myProcessingWeight) return myDataClass.compareTo(that.myDataClass);
    return myProcessingWeight - that.myProcessingWeight;
  }

  @Override
  public String toString() {
    int i = myDataClass.lastIndexOf('.');
    return i > 0 ? myDataClass.substring(i + 1) : myDataClass;
  }
}
