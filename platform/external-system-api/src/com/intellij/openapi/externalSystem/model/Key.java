// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.serialization.PropertyMapping;
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
 * @param <T>  data class
 */
@SuppressWarnings("UnusedDeclaration")
public final class Key<T> implements Comparable<Key<?>>, Serializable {
  @NotNull private final String dataClass;

  private final int processingWeight;

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
  @PropertyMapping({"dataClass", "processingWeight"})
  public Key(@NotNull String dataClass, int processingWeight) {
    this.dataClass = dataClass;
    this.processingWeight = processingWeight;
  }

  @NotNull
  public static <T> Key<T> create(@NotNull Class<T> dataClass, int processingWeight) {
    return new Key<>(dataClass.getName(), processingWeight);
  }

  @NotNull
  public String getDataType() {
    return dataClass;
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
    return processingWeight;
  }

  @Override
  public int hashCode() {
    return dataClass.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Key key = (Key)o;

    if (!dataClass.equals(key.dataClass)) return false;

    return true;
  }

  @Override
  public int compareTo(@NotNull Key<?> that) {
    if(processingWeight == that.processingWeight) return dataClass.compareTo(that.dataClass);
    return processingWeight - that.processingWeight;
  }

  @Override
  public @NlsSafe String toString() {
    int i = dataClass.lastIndexOf('.');
    return i > 0 ? dataClass.substring(i + 1) : dataClass;
  }
}
