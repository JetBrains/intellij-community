/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 27, 2004
 */
public class Chunk<Node> {
  private final Set<Node> myNodes;

  public Chunk(Node node) {
    this(new LinkedHashSet<Node>());
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
    final StringBuilder buf = new StringBuilder();
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
