package com.intellij.internal.diGraph.analyzer;

import com.intellij.internal.diGraph.Edge;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 21.06.2003
 * Time: 22:29:04
 * To change this template use Options | File Templates.
 */
public interface MarkedEdge extends Edge {
  Mark getMark();
  void setMark(Mark x);
}
