/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler;

import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 27, 2004
 */
public class Chunk<Node> {
  private final Set<Node> myNodes;

  public Chunk(Node node) {
    this(new HashSet<Node>());
    myNodes.add(node);
  }
  
  public Chunk(Set<Node> nodes) {
    myNodes = nodes;
  }

  public Set<Node> getNodes() {
    return myNodes;
  }

  public boolean containsNode(Node node) {
    return myNodes.contains(node);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Chunk)) return false;

    final Chunk chunk = (Chunk)o;

    if (!myNodes.equals(chunk.myNodes)) return false;

    return true;
  }

  public int hashCode() {
    return myNodes.hashCode();
  }

  public String toString() { // for debugging only
    final StringBuffer buf = new StringBuffer();
    buf.append("[");
    for (final Node node : myNodes) {
      if (buf.length() > 1) {
        buf.append(", ");
      }
      buf.append(node.toString());
    }
    buf.append("]");
    return buf.toString();
  }
}
