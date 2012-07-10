/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup;

import com.intellij.psi.ForceableComparable;
import com.intellij.util.ProcessingContext;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
* @author peter
*/
public class CachingComparingClassifier extends ComparingClassifier<LookupElement> {
  private final Map<LookupElement, Comparable> myWeights = new THashMap<LookupElement, Comparable>(TObjectHashingStrategy.IDENTITY);
  private final LookupElementWeigher myWeigher;
  private Comparable myFirstWeight;
  private boolean myPrimitive = true;

  public CachingComparingClassifier(Classifier<LookupElement> next, LookupElementWeigher weigher) {
    super(next, weigher.toString());
    myWeigher = weigher;
  }

  @NotNull
  @Override
  public final Comparable getWeight(LookupElement t) {
    final Comparable weight = myWeights.get(t);
    if (weight == null) {
      throw new AssertionError(myName + "; " + myWeights.containsKey(t) + "; element=" + t);
    }
    return weight;
  }

  @Override
  public Iterable<LookupElement> classify(Iterable<LookupElement> source, ProcessingContext context) {
    if (myPrimitive) {
      return myNext.classify(source, context);
    }

    return super.classify(source, context);
  }

  @Override
  public void addElement(LookupElement t) {
    Comparable weight = myWeigher.weigh(t);
    if (weight instanceof ForceableComparable) {
      ((ForceableComparable)weight).force();
    }
    if (myPrimitive) {
      if (myFirstWeight == null) {
        myFirstWeight = weight;
      } else if (!myFirstWeight.equals(weight)) {
        myPrimitive = false;
      }
    }
    myWeights.put(t, weight);
    super.addElement(t);
  }

}
