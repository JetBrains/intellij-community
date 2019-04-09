// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodWithResultObject;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiVariable;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Pavel.Dolgov
 */
class ExtractionContext {
  final Map<PsiReferenceExpression, PsiVariable> myInputs = new HashMap<>();
  final Set<PsiVariable> myOutputVariables = new HashSet<>();
  final Map<PsiStatement, Exit> myExits = new HashMap<>();
  final Set<PsiVariable> myWrittenOuterVariables = new HashSet<>();

  void addExit(@Nullable PsiStatement statement, @NotNull ExitType exitType, @Nullable PsiElement element) {
    myExits.put(statement, new Exit(exitType, element));
  }

  Set<ExitType> getExitTypes() {
    return myExits.values().stream().map(Exit::getType).collect(Collectors.toSet());
  }

  /**
   * @return mapping: exit -> exit key
   */
  @NotNull
  Map<Exit, Integer> getDistinctExits() {
    Map<Exit, Integer> distinctExits = new HashMap<>();
    EntryStream.of(myExits)
      .mapKeys(s -> s != null ? s.getTextOffset() : 0)
      .sorted(Comparator.comparing((Function<Map.Entry<Integer, Exit>, Integer>)Map.Entry::getKey)
                .thenComparing(e -> e.getValue().getType()))
      .forKeyValue((offset, exit) -> {
        if (!distinctExits.containsKey(exit)) {
          distinctExits.put(exit, distinctExits.size());
        }
      });
    return distinctExits;
  }
}
