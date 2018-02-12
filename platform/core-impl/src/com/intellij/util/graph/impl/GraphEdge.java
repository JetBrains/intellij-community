// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph.impl;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
class GraphEdge<Node> {
  private final Node myStart;
  private final Node myFinish;
  private final int myDelta;

  GraphEdge(@NotNull Node start, @NotNull Node finish, int delta) {
    myStart = start;
    myFinish = finish;
    myDelta = delta;
  }

  public Node getStart() {
    return myStart;
  }

  public Node getFinish() {
    return myFinish;
  }

  public int getDelta() {
    return myDelta;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GraphEdge edge = (GraphEdge)o;
    return myFinish.equals(edge.myFinish) && myStart.equals(edge.myStart);
  }

  @Override
  public int hashCode() {
    return 31 * myStart.hashCode() + myFinish.hashCode();
  }
}
