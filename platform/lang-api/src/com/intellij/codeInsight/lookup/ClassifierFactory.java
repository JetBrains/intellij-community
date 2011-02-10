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

import com.intellij.openapi.util.NotNullFactory;
import com.intellij.util.SmartList;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;

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

  public abstract Classifier<T> createClassifier(NotNullFactory<Classifier<T>> next);

  public static <T> NotNullFactory<Classifier<T>> listClassifier() {
    return new NotNullFactory<Classifier<T>>() {
      @NotNull
      @Override
      public Classifier<T> create() {
        return new ListClassifier<T>(new SmartList<T>());
      }
    };
  }

  public static <T> NotNullFactory<Classifier<T>> sortingListClassifier(final Comparator<T> comparator) {
    return new NotNullFactory<Classifier<T>>() {
      @NotNull
      @Override
      public Classifier<T> create() {
        return new ListClassifier<T>(new SortedList<T>(comparator));
      }
    };
  }

  private static class ListClassifier<T> implements Classifier<T> {
    private final List<T> myElements;

    private ListClassifier(final List<T> list) {
      myElements = list;
    }

    @Override
    public void addElement(T t) {
      myElements.add(t);
    }

    @Override
    public List<List<T>> classifyContents() {
      return Collections.singletonList(myElements);
    }
  }
}
