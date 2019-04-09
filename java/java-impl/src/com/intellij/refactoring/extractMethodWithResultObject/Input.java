// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodWithResultObject;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Pavel.Dolgov
 */
class Input implements Comparable<Input> {
  private final String myName;
  private final List<PsiExpression> myOccurrences = new ArrayList<>();

  Input(@NotNull String name) {
    myName = name;
  }

  String getName() {
    return myName;
  }

  List<PsiExpression> getOccurrences() {
    return myOccurrences;
  }

  void addOccurrence(PsiReferenceExpression occurrence) {
    myOccurrences.add(occurrence);
  }

  int getFirstOccurrenceOffset() {
    return myOccurrences.stream().mapToInt(PsiElement::getTextOffset).min().orElse(-1);
  }

  @Override
  public int compareTo(@NotNull Input input) {
    int c = Integer.compare(getFirstOccurrenceOffset(), input.getFirstOccurrenceOffset());
    return c != 0 ? c : myName.compareTo(input.myName);
  }
}
