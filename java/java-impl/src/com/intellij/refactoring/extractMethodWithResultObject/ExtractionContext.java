// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodWithResultObject;

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiVariable;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * @author Pavel.Dolgov
 */
class ExtractionContext {
  final Map<PsiVariable, Input> myInputs = new HashMap<>();
  final Set<PsiVariable> myOutputVariables = new HashSet<>();
  final List<PsiClassType> myThrownCheckedExceptions = new ArrayList<>();
  final Map<PsiStatement, Exit> myExits = new HashMap<>();
  final Set<PsiVariable> myWrittenOuterVariables = new HashSet<>();
  final Map<Exit, Integer> myDistinctExits = new HashMap<>();
  final List<ResultItem> myResultItems = new ArrayList<>();
  final Map<PsiStatement, Output> myOutputs = new HashMap<>();

  ExtractionContext(Collection<PsiVariable> inputVariables) {
    for (PsiVariable variable : inputVariables) {
      myInputs.put(variable, new Input(notNull(variable.getName())));
    }
  }

  void addExit(@Nullable PsiStatement statement, @NotNull ExitType exitType, @Nullable PsiElement element) {
    myExits.put(statement, new Exit(exitType, element));
  }

  Set<ExitType> getExitTypes() {
    return myExits.values().stream().map(Exit::getType).collect(Collectors.toSet());
  }

  EntryStream<PsiVariable, String> getOrderedInputVariables() {
    return EntryStream.of(myInputs)
      .sorted(Comparator.comparing(Map.Entry::getValue))
      .mapValues(Input::getName);
  }
}
