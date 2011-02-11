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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author peter
 */
public abstract class ClassifierFactory<T> {
  private final String myId;

  protected ClassifierFactory(String id) {
    myId = id;
  }

  public String getId() {
    return myId;
  }

  public abstract Classifier<T> createClassifier(Classifier<T> next);

  public static <T> Classifier<T> listClassifier() {
    return new Classifier<T>() {
      @Override
      public void addElement(T t) {
      }

      @Override
      public Iterable<List<T>> classify(List<T> source) {
        return Collections.singleton(source);
      }
    };
  }

  public static <T> Classifier<T> sortingListClassifier(final Comparator<T> comparator) {
    return new Classifier<T>() {
      @Override
      public void addElement(T t) {
      }

      @Override
      public Iterable<List<T>> classify(List<T> source) {
        final List<T> copy = new ArrayList<T>(source);
        Collections.sort(source, comparator);
        return Collections.singletonList(copy);
      }
    };
  }

}
