// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.diGraph.analyzer;

import com.intellij.openapi.util.Pair;

import java.util.Iterator;
import java.util.LinkedList;

public final class GlobalAnalyzer {
  private static boolean stepOneEnd(MarkedNode currNode, LinkedList worklist, OneEndFunctor functor) {
    boolean result = false;

    for (Iterator i = currNode.outIterator(); i.hasNext();) {
      MarkedEdge currEdge = (MarkedEdge)i.next();
      MarkedNode nextNode = (MarkedNode)currEdge.end();
      Mark theMark = functor.compute(currNode.getMark(), currEdge.getMark(), nextNode.getMark());
      if (!theMark.coincidesWith(nextNode.getMark())) {
        result = true;
        nextNode.setMark(theMark);
        worklist.addFirst(nextNode);
      }
    }

    return result;
  }

  private static boolean stepTwoEnds(final MarkedNode currNode, final LinkedList worklist, final TwoEndsFunctor functor) {
    boolean result = false;

    for (Iterator i = currNode.outIterator(); i.hasNext();) {
      final MarkedEdge currEdge = (MarkedEdge)i.next();
      final MarkedNode nextNode = (MarkedNode)currEdge.end();
      final Pair<Mark,Mark> markPair = functor.compute(currNode.getMark(), currEdge.getMark(), nextNode.getMark());

      final Mark leftMark = markPair.getFirst();
      final Mark rightMark = markPair.getSecond();

      if (!leftMark.coincidesWith(currNode.getMark())) {
        result = true;
        currNode.setMark(leftMark);
        worklist.addFirst(currNode);
      }

      if (!rightMark.coincidesWith(nextNode.getMark())) {
        result = true;
        nextNode.setMark(rightMark);
        worklist.addFirst(nextNode);
      }
    }

    return result;
  }

  public static <T extends MarkedNode>boolean doOneEnd(final LinkedList<T> init, final OneEndFunctor functor) {
    boolean result = false;

    final LinkedList<T> worklist = new LinkedList<>();

    for (T anInit : init) {
      result = stepOneEnd(anInit, worklist, functor) || result;
    }

    while (!worklist.isEmpty()) {
      result = stepOneEnd(worklist.removeFirst(), worklist, functor) || result;
    }

    return result;
  }

  public static <T extends MarkedNode>boolean doTwoEnds(final LinkedList<T> init, final TwoEndsFunctor functor) {
    boolean result = false;

    final LinkedList<T> worklist = new LinkedList<>();

    for (T anInit : init) {
      result = stepTwoEnds(anInit, worklist, functor) || result;
    }

    while (!worklist.isEmpty()) {
      result = stepTwoEnds(worklist.removeFirst(), worklist, functor) || result;
    }

    return result;
  }
}
