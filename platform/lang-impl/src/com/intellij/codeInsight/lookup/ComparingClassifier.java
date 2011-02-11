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

import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;

import java.util.*;

/**
* @author peter
*/
public abstract class ComparingClassifier<T> extends Classifier<T> {
  private final Map<T, Comparable> myWeights = new HashMap<T, Comparable>();
  private final Classifier<T> myNext;

  public ComparingClassifier(Classifier<T> next) {
    myNext = next;
  }

  public abstract Comparable getWeight(T t);

  @Override
  public void addElement(T t) {
    myWeights.put(t, getWeight(t));
    myNext.addElement(t);
  }

  @Override
  public Iterable<List<T>> classify(List<T> source) {
    List<List<T>> result = new ArrayList<List<T>>();
    TreeMap<Comparable, List<T>> map = new TreeMap<Comparable, List<T>>();
    for (T t : source) {
      final Comparable weight = myWeights.get(t);
      List<T> list = map.get(weight);
      if (list == null) {
        map.put(weight, list = new SmartList<T>());
      }
      list.add(t);
    }
    for (List<T> list : map.values()) {
      ContainerUtil.addAll(result, myNext.classify(list));
    }
    return result;
  }
}
