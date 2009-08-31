package com.intellij.internal.diGraph.analyzer;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 10.06.2003
 * Time: 23:40:39
 * To change this template use Options | File Templates.
 */
public interface Mark {
  boolean coincidesWith(Mark x);
}
