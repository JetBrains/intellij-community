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
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public abstract class ComparingClassifier<T> extends Classifier<T> {
  protected final Classifier<T> myNext;
  protected final String myName;
  private final boolean myNegated;

  public ComparingClassifier(Classifier<T> next, String name) {
    this(next, name, false);
  }

  protected ComparingClassifier(Classifier<T> next, String name, boolean negated) {
    myNext = next;
    myName = name;
    myNegated = negated;
  }

  @Nullable
  public abstract Comparable getWeight(T t);

  public void addElement(T t) {
    myNext.addElement(t);
  }

  @Override
  public Iterable<T> classify(Iterable<T> source, ProcessingContext context) {
    List<T> nulls = null;
    TreeMap<Comparable, List<T>> map = new TreeMap<Comparable, List<T>>();
    for (T t : myNext.classify(source, context)) {
      final Comparable weight = getWeight(t);
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

    final Collection<List<T>> values = myNegated ? map.descendingMap().values() : map.values();
    final List<T> lastGroup = nulls == null ? Collections.<T>emptyList() : nulls;

    return new Iterable<T>() {

      @Override
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          private Iterator<List<T>> valuesIterator = values.iterator();
          private Iterator<T> groupIterator = Collections.<T>emptyList().iterator();
          private boolean passedLast;

          @Override
          public boolean hasNext() {
            while (!groupIterator.hasNext() && valuesIterator.hasNext()) {
              groupIterator = valuesIterator.next().iterator();
            }
            if (!groupIterator.hasNext() && !valuesIterator.hasNext() && !passedLast) {
              passedLast = true;
              groupIterator = lastGroup.iterator();
            }
            return groupIterator.hasNext();
          }

          @Override
          public T next() {
            if (!hasNext()) {
              throw new AssertionError();
            }
            return groupIterator.next();
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  @Override
  public void describeItems(LinkedHashMap<T, StringBuilder> map, ProcessingContext context) {
    Map<T, String> weights = new IdentityHashMap<T, String>();
    for (T t : map.keySet()) {
      weights.put(t, String.valueOf(getWeight(t)));
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
    myNext.describeItems(map, context);
  }
}
