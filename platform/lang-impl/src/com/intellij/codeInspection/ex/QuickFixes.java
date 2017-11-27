/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.ex;

import org.jetbrains.annotations.NotNull;

import java.util.function.ObjIntConsumer;


public interface QuickFixes {
  QuickFixes EMPTY = new QuickFixes() {
    @Override
    public void processFixes(@NotNull ObjIntConsumer<QuickFixAction> fixConsumer) { }
  };

  class QuickFixesImpl implements QuickFixes {
    private final InspectionRVContentProvider.FixAndOccurrences[] myFixOccurrences;

    public QuickFixesImpl(@NotNull InspectionRVContentProvider.FixAndOccurrences[] occurrences) {myFixOccurrences = occurrences;}

    @Override
    public void processFixes(@NotNull ObjIntConsumer<QuickFixAction> fixConsumer) {
      for (InspectionRVContentProvider.FixAndOccurrences fixAndOccurrence : myFixOccurrences) {
        fixConsumer.accept(fixAndOccurrence.fix, fixAndOccurrence.occurrences);
      }
    }
  }

  void processFixes(@NotNull ObjIntConsumer<QuickFixAction> fixConsumer);
}
