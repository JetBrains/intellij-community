package com.intellij.codeInsight.completion.methodChains.search;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class ChainRelevance implements Comparable<ChainRelevance> {
  public static final ChainRelevance LOWEST = new ChainRelevance(Integer.MAX_VALUE, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, false, false, 0);

  private final int myChainSize;
  private final int myLastMethodOccurrences;
  private final int myUnreachableParametersCount;
  private final int myNotMatchedStringVars;
  private final boolean myHasCallingVariableInContext;
  private final boolean myFirstMethodStatic;
  private final int myParametersInContext;

  public ChainRelevance(final int chainSize,
                        final int lastMethodOccurrences,
                        final int unreachableParametersCount,
                        final int notMatchedStringVars,
                        final boolean hasCallingVariableInContext,
                        final boolean firstMethodStatic,
                        final int parametersInContext) {
    myChainSize = chainSize;
    myLastMethodOccurrences = lastMethodOccurrences;
    myUnreachableParametersCount = unreachableParametersCount;
    myNotMatchedStringVars = notMatchedStringVars;
    myHasCallingVariableInContext = hasCallingVariableInContext;
    myFirstMethodStatic = firstMethodStatic;
    myParametersInContext = parametersInContext;
  }

  @TestOnly
  public boolean hasCallingVariableInContext() {
    return myHasCallingVariableInContext;
  }

  @TestOnly
  public int getChainSize() {
    return myChainSize;
  }

  @TestOnly
  public int getLastMethodOccurrences() {
    return myLastMethodOccurrences;
  }

  @TestOnly
  public int getUnreachableParametersCount() {
    return myUnreachableParametersCount;
  }

  @TestOnly
  public int getNotMatchedStringVars() {
    return myNotMatchedStringVars;
  }

  @TestOnly
  public boolean isFirstMethodStatic() {
    return myFirstMethodStatic;
  }

  @Override
  public int compareTo(@NotNull final ChainRelevance that) {
    if (myHasCallingVariableInContext && !that.myHasCallingVariableInContext) {
      return 1;
    }
    if (that.myHasCallingVariableInContext && !myHasCallingVariableInContext) {
      return -1;
    }
    if (myFirstMethodStatic && !that.myFirstMethodStatic) {
      return -1;
    }
    if (that.myFirstMethodStatic && !myFirstMethodStatic) {
      return 1;
    }
    if (myParametersInContext > that.myParametersInContext) {
      return 1;
    }
    if (myParametersInContext <= that.myParametersInContext) {
      return -1;
    }
    int sub = myLastMethodOccurrences - that.myLastMethodOccurrences;
    if (sub != 0) return sub;
    sub = myUnreachableParametersCount - that.myUnreachableParametersCount;
    if (sub != 0) return -sub;
    return 0;
  }

  @Override
  public String toString() {
    return (myFirstMethodStatic ? "1" : "0") + 
           (myHasCallingVariableInContext ? "1" : "0") + "_" + 
           myLastMethodOccurrences + "_" + 
           myUnreachableParametersCount;
  }
}
