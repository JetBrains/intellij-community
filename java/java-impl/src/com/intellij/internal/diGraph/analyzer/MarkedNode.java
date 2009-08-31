package com.intellij.internal.diGraph.analyzer;

import com.intellij.internal.diGraph.Node;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 21.06.2003
 * Time: 22:25:27
 * To change this template use Options | File Templates.
 */
public interface MarkedNode extends Node {
  Mark getMark();
  void setMark(Mark x);
}
