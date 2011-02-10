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
import com.intellij.openapi.util.NotNullFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ClassifyingSequencer {
  private final List<ClassifierFactory<LookupElement>> myMembers;

  public ClassifyingSequencer() {
    this(new ArrayList<ClassifierFactory<LookupElement>>());
  }

  private ClassifyingSequencer(final List<ClassifierFactory<LookupElement>> members) {
    myMembers = members;
  }

  public ClassifyingSequencer withClassifier(ClassifierFactory<LookupElement> classifierFactory) {
    return enhanced(classifierFactory, myMembers.size());
  }

  public ClassifyingSequencer withClassifier(ClassifierFactory<LookupElement> classifierFactory,
                                             @NotNull String anchorId,
                                             boolean beforeAnchor) {
    final int i = idIndex(anchorId);
    return enhanced(classifierFactory, beforeAnchor ? Math.max(0, i) : i + 1);
  }

  private ClassifyingSequencer enhanced(ClassifierFactory<LookupElement> classifierFactory, int index) {
    final List<ClassifierFactory<LookupElement>> copy = new ArrayList<ClassifierFactory<LookupElement>>(myMembers);
    copy.add(index, classifierFactory);
    return new ClassifyingSequencer(copy);
  }


  private int idIndex(final String id) {
    return ContainerUtil.indexOf(myMembers, new Condition<ClassifierFactory<LookupElement>>() {
      @Override
      public boolean value(ClassifierFactory<LookupElement> lookupElementClassifierFactory) {
        return id.equals(lookupElementClassifierFactory.getId());
      }
    });
  }

  private static <T> Classifier<T> createClassifier(final int index, final List<ClassifierFactory<T>> components) {
    if (index == components.size()) {
      return ClassifierFactory.<T>listClassifier().create();
    }

    return components.get(index).createClassifier(new NotNullFactory<Classifier<T>>() {
      @Override
      public Classifier<T> create() {
        return createClassifier(index + 1, components);
      }
    });
  }

  Classifier buildClassifier() {
    return createClassifier(0, myMembers);
  }


}

