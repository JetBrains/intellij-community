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

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ClassifyingSequencer<T> {
  private final List<ClassifierFactory<T>> myMembers;

  public ClassifyingSequencer() {
    this(new ArrayList<ClassifierFactory<T>>());
  }

  private ClassifyingSequencer(final List<ClassifierFactory<T>> members) {
    myMembers = members;
  }

  public ClassifyingSequencer<T> withClassifier(ClassifierFactory<T> classifierFactory) {
    return enhanced(classifierFactory, myMembers.size());
  }

  public ClassifyingSequencer<T> withClassifier(ClassifierFactory<T> classifierFactory,
                                             @NotNull String anchorId,
                                             boolean beforeAnchor) {
    final int i = idIndex(anchorId);
    return enhanced(classifierFactory, beforeAnchor ? Math.max(0, i) : i + 1);
  }

  private ClassifyingSequencer<T> enhanced(ClassifierFactory<T> classifierFactory, int index) {
    final List<ClassifierFactory<T>> copy = new ArrayList<ClassifierFactory<T>>(myMembers);
    copy.add(index, classifierFactory);
    return new ClassifyingSequencer<T>(copy);
  }


  private int idIndex(final String id) {
    return ContainerUtil.indexOf(myMembers, new Condition<ClassifierFactory<T>>() {
      @Override
      public boolean value(ClassifierFactory<T> lookupElementClassifierFactory) {
        return id.equals(lookupElementClassifierFactory.getId());
      }
    });
  }

  private static <T> Classifier<T> createClassifier(final int index, final List<ClassifierFactory<T>> components) {
    if (index == components.size()) {
      return ClassifierFactory.listClassifier();
    }

    return components.get(index).createClassifier(createClassifier(index + 1, components));
  }

  public Classifier<T> buildClassifier() {
    return createClassifier(0, myMembers);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClassifyingSequencer)) return false;

    ClassifyingSequencer that = (ClassifyingSequencer)o;

    if (!myMembers.equals(that.myMembers)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myMembers.hashCode();
  }
}

