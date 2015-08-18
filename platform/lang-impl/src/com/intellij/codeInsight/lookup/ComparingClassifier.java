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
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FlatteningIterator;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public abstract class ComparingClassifier<T> extends Classifier<T> {
  protected final String myName;
  private final boolean myNegated;

  public ComparingClassifier(Classifier<T> next, String name) {
    this(next, name, false);
  }

  protected ComparingClassifier(Classifier<T> next, String name, boolean negated) {
    super(next);
    myName = name;
    myNegated = negated;
  }

  @Nullable
  public abstract Comparable getWeight(T t, ProcessingContext context);

  @Override
  public Iterable<T> classify(final Iterable<T> source, final ProcessingContext context) {
    List<T> nulls = null;
    TreeMap<Comparable, List<T>> map = new TreeMap<Comparable, List<T>>();
    for (T t : source) {
      final Comparable weight = getWeight(t, context);
      if (weight == null) {
        if (nulls == null) nulls = new SmartList<T>();
        nulls.add(t);
      } else {
        List<T> list = map.get(weight);
        if (list == null) {
          map.put(weight, list = new SmartList<T>());
        }
        list.add(t);
      }
    }

    final List<List<T>> values = new ArrayList<List<T>>();
    values.addAll(myNegated ? map.descendingMap().values() : map.values());
    ContainerUtil.addIfNotNull(values, nulls);

    return new Iterable<T>() {
      @Override
      public Iterator<T> iterator() {
        return new FlatteningIterator<List<T>, T>(values.iterator()) {
          @Override
          protected Iterator<T> createValueIterator(List<T> group) {
            return myNext.classify(group, context).iterator();
          }
        };
      }
    };
  }

  @Override
  public void describeItems(LinkedHashMap<T, StringBuilder> map, ProcessingContext context) {
    Map<T, String> weights = new IdentityHashMap<T, String>();
    for (T t : map.keySet()) {
      weights.put(t, String.valueOf(getWeight(t, context)));
    }
    if (new HashSet<String>(weights.values()).size() > 1 || ApplicationManager.getApplication().isUnitTestMode()) {
      for (T t : map.keySet()) {
        final StringBuilder builder = map.get(t);
        if (builder.length() > 0) {
          builder.append(", ");
        }
        builder.append(myName).append("=").append(weights.get(t));
      }
    }
    super.describeItems(map, context);
  }
}
