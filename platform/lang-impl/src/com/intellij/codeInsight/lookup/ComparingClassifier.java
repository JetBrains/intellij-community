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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author peter
 */
public abstract class ComparingClassifier<T> extends Classifier<T> {
  private final Classifier<T> myNext;
  protected final String myName;

  public ComparingClassifier(Classifier<T> next, String name) {
    myNext = next;
    myName = name;
  }

  @NotNull
  public abstract Comparable getWeight(T t);

  public void addElement(T t) {
    myNext.addElement(t);
  }

  private TreeMap<Comparable, List<T>> groupByWeights(List<T> source) {
    TreeMap<Comparable, List<T>> map = new TreeMap<Comparable, List<T>>();
    for (T t : source) {
      final Comparable weight = getWeight(t);
      List<T> list = map.get(weight);
      if (list == null) {
        map.put(weight, list = new SmartList<T>());
      }
      list.add(t);
    }
    return map;
  }

  @Override
  public Iterable<List<T>> classify(List<T> source) {
    List<List<T>> result = new ArrayList<List<T>>();
    for (List<T> list : groupByWeights(source).values()) {
      ContainerUtil.addAll(result, myNext.classify(list));
    }
    return result;
  }

  @Override
  public void describeItems(LinkedHashMap<T, StringBuilder> map) {
    final TreeMap<Comparable, List<T>> treeMap = groupByWeights(new ArrayList<T>(map.keySet()));
    if (treeMap.size() > 1 || ApplicationManager.getApplication().isUnitTestMode()) {
      for (Map.Entry<Comparable, List<T>> entry: treeMap.entrySet()){
        for (T t : entry.getValue()) {
          final StringBuilder builder = map.get(t);
          if (builder.length() > 0) {
            builder.append(", ");
          }

          builder.append(myName).append("=").append(entry.getKey());
        }
      }
    }
    myNext.describeItems(map);
  }
}
