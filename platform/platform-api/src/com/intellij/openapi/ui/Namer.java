/*
 * @author max
 */
package com.intellij.openapi.ui;

public interface Namer<T> {
    String getName(T t);
    boolean canRename(T item);
    void setName(T t, String name);
}