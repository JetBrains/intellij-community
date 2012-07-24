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

import com.intellij.codeInsight.completion.CompletionLookupArranger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.ForceableComparable;
import com.intellij.util.ProcessingContext;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
* @author peter
*/
public class CachingComparingClassifier extends ComparingClassifier<LookupElement> {
  private final Map<LookupElement, Comparable> myWeights = new IdentityHashMap<LookupElement, Comparable>();
  private final LookupElementWeigher myWeigher;
  private Ref<Comparable> myFirstWeight;
  private boolean myPrimitive = true;
  private int myPrefixChanges = -1;

  public CachingComparingClassifier(Classifier<LookupElement> next, LookupElementWeigher weigher) {
    super(next, weigher.toString(), weigher.isNegated());
    myWeigher = weigher;
  }

  @Override
  public final Comparable getWeight(LookupElement t) {
    Comparable w = myWeights.get(t);
    if (w == null && myWeigher.isPrefixDependent()) {
      myWeights.put(t, w = myWeigher.weigh(t));
    }
    return w;
  }

  @Override
  public Iterable<LookupElement> classify(Iterable<LookupElement> source, ProcessingContext context) {
    if (!myWeigher.isPrefixDependent() && myPrimitive) {
      return myNext.classify(source, context);
    }
    checkPrefixChanged(context);

    return super.classify(source, context);
  }

  private void checkPrefixChanged(ProcessingContext context) {
    int actualPrefixChanges = context.get(CompletionLookupArranger.PREFIX_CHANGES).intValue();
    if (myWeigher.isPrefixDependent() && myPrefixChanges != actualPrefixChanges) {
      myPrefixChanges = actualPrefixChanges;
      myWeights.clear();
    }
  }

  @Override
  public void describeItems(LinkedHashMap<LookupElement, StringBuilder> map, ProcessingContext context) {
    checkPrefixChanged(context);
    super.describeItems(map, context);
  }

  @Override
  public void addElement(LookupElement t) {
    Comparable weight = myWeigher.weigh(t);
    if (weight instanceof ForceableComparable) {
      ((ForceableComparable)weight).force();
    }
    if (!myWeigher.isPrefixDependent() && myPrimitive) {
      if (myFirstWeight == null) {
        myFirstWeight = Ref.create(weight);
      } else if (!Comparing.equal(myFirstWeight.get(), weight)) {
        myPrimitive = false;
      }
    }
    myWeights.put(t, weight);
    super.addElement(t);
  }

}
