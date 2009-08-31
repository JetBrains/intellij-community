package com.intellij.internal.diGraph.analyzer;

import com.intellij.openapi.util.Pair;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 18.12.2003
 * Time: 19:00:35
 * To change this template use Options | File Templates.
 */
public interface TwoEndsFunctor {
  Pair<Mark,Mark> compute(Mark from, Mark edge, Mark to);
}
