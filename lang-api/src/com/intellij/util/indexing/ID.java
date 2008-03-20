package com.intellij.util.indexing;

import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 12, 2008
 */
public class ID<K, V> {
  private static int ourCounter = 0;
  
  private final int myIndex = ourCounter++;
  private final String myName;

  public ID(String name) {
    myName = name;
  }

  public static <K, V> ID<K, V> create(@NonNls String name) {
    return new ID<K,V>(name);
  }

  public int hashCode() {
    return myIndex;
  }

  public String toString() {
    return myName;
  }
}
