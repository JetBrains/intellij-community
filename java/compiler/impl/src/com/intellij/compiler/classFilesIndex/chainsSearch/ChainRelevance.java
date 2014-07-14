/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.classFilesIndex.chainsSearch;

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
