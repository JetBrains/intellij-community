// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.execution.filters.ExceptionAnalysisProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavaValueFilter implements SliceValueFilter {
  public static final JavaValueFilter ALLOW_EVERYTHING = new JavaValueFilter(null, null);

  private final @Nullable DfaBasedFilter myDfaFilter;
  private final @Nullable StackFilter myStackFilter;

  JavaValueFilter(@Nullable DfaBasedFilter filter,
                  @Nullable StackFilter stackFilter) {
    myDfaFilter = filter;
    myStackFilter = stackFilter;
  }

  JavaValueFilter(@NotNull DfType dfType) {
    this(new DfaBasedFilter(dfType), null);
  }

  @Override
  public boolean allowed(@NotNull PsiElement element) {
    return (myDfaFilter == null || myDfaFilter.allowed(element)) &&
           (myStackFilter == null || myStackFilter.isAcceptable(element));
  }

  public @NotNull JavaValueFilter withStack(List<ExceptionAnalysisProvider.StackLine> lines) {
    return new JavaValueFilter(myDfaFilter, StackFilter.from(lines));
  }

  @NotNull JavaValueFilter withType(DfType type) {
    return new JavaValueFilter(new DfaBasedFilter(type), myStackFilter);
  }

  @NotNull JavaValueFilter pushFrame() {
    if (myStackFilter == null) return this;
    return new JavaValueFilter(myDfaFilter, myStackFilter.pushFrame());
  }

  @NotNull JavaValueFilter popFrame(Project project) {
    if (myStackFilter == null) return this;
    return new JavaValueFilter(myDfaFilter, myStackFilter.popFrame(project));
  }

  @NotNull JavaValueFilter dropFrameFilter() {
    if (myStackFilter == null) return this;
    return new JavaValueFilter(myDfaFilter, null);
  }

  @NotNull JavaValueFilter wrap() {
    if (myDfaFilter == null) return this;
    return new JavaValueFilter(myDfaFilter.wrap(), myStackFilter);
  }

  @NotNull JavaValueFilter unwrap() {
    if (myDfaFilter == null) return this;
    return new JavaValueFilter(myDfaFilter.unwrap(), myStackFilter);
  }

  @NotNull SearchScope correctScope(@NotNull SearchScope scope) {
    return myStackFilter == null ? scope : myStackFilter.correctScope(scope);
  }

  @Override
  public @NotNull @Nls String getPresentationText(@NotNull PsiElement element) {
    // For now, stack filter doesn't contribute to the presentation text
    return myDfaFilter == null ? "" : myDfaFilter.getPresentationText(element);
  }

  JavaValueFilter mergeFilter(PsiElement expression) {
    DfaBasedFilter dfaFilter = myDfaFilter == null ? new DfaBasedFilter(DfType.TOP) : myDfaFilter;
    DfaBasedFilter newFilter = dfaFilter.mergeFilter(expression);
    return dfaFilter == newFilter ? this : new JavaValueFilter(newFilter, myStackFilter);
  }

  DfType getDfType() {
    return myDfaFilter == null ? DfType.TOP : myDfaFilter.getDfType();
  }

  @Override
  public String toString() {
    return ((myDfaFilter == null ? "" : myDfaFilter.toString()) +
           " " + (myStackFilter == null ? "" : myStackFilter.toString())).trim();
  }

  public boolean requiresAssertionViolation(PsiElement element) {
    return myDfaFilter != null && myDfaFilter.requiresAssertionViolation(element);
  }

  JavaValueFilter copyStackFrom(SliceValueFilter filter) {
    if (filter instanceof JavaValueFilter && ((JavaValueFilter)filter).myStackFilter != myStackFilter) {
      return new JavaValueFilter(myDfaFilter, ((JavaValueFilter)filter).myStackFilter);
    }
    return this;
  }
}
