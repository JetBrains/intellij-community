// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import gnu.trove.THashMap;
import org.intellij.lang.annotations.Flow;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.Map;

/**
 * A helper class to build new {@link JavaSliceUsage} and pass it to the processor
 */
final class JavaSliceBuilder {
  private final @NotNull SliceUsage myParent;
  private final @NotNull PsiSubstitutor mySubstitutor;
  private final @Range(from = 0, to = Integer.MAX_VALUE) int myIndexNesting;
  private final @NotNull String mySyntheticField;
  private final @NotNull SliceAnalysisParams myAnalysisParams;

  private JavaSliceBuilder(@NotNull SliceUsage parent,
                           @NotNull PsiSubstitutor substitutor,
                           @Range(from = 0, to = Integer.MAX_VALUE) int indexNesting,
                           @NotNull String syntheticField,
                           @NotNull SliceAnalysisParams analysisParams) {
    assert indexNesting >= 0 : indexNesting;
    myParent = parent;
    mySubstitutor = substitutor;
    myIndexNesting = indexNesting;
    mySyntheticField = syntheticField;
    myAnalysisParams = analysisParams;
  }

  @Contract(pure = true)
  @NotNull JavaSliceBuilder withSubstitutor(@NotNull PsiSubstitutor substitutor) {
    return new JavaSliceBuilder(myParent, substitutor, myIndexNesting, mySyntheticField, myAnalysisParams);
  }

  @Contract(pure = true)
  @NotNull JavaSliceBuilder withSyntheticField(@NotNull String syntheticField) {
    if (syntheticField.equals(mySyntheticField)) return this;
    return new JavaSliceBuilder(myParent, mySubstitutor, myIndexNesting, syntheticField, myAnalysisParams);
  }

  @Contract(pure = true)
  @NotNull JavaSliceBuilder dropSyntheticField() {
    return withSyntheticField("");
  }

  @Contract(pure = true)
  @NotNull JavaSliceBuilder dropNesting() {
    if (myIndexNesting == 0) return this;
    return new JavaSliceBuilder(myParent, mySubstitutor, 0, mySyntheticField, myAnalysisParams)
      .withFilter(null);
  }

  @Contract(pure = true)
  @NotNull JavaSliceBuilder withFilter(@Nullable SliceValueFilter filter) {
    if (filter == myAnalysisParams.valueFilter) return this;
    SliceAnalysisParams params = new SliceAnalysisParams(myAnalysisParams);
    params.valueFilter = filter;
    return new JavaSliceBuilder(myParent, mySubstitutor, myIndexNesting, mySyntheticField, params);
  }

  boolean process(PsiElement element, Processor<? super SliceUsage> processor) {
    final PsiElement realExpression = element.getParent() instanceof DummyHolder ? element.getParent().getContext() : element;
    assert realExpression != null;
    if (!(realExpression instanceof PsiCompiledElement)) {
      JavaDfaSliceValueFilter curFilter = myAnalysisParams.valueFilter instanceof JavaDfaSliceValueFilter
                                          ? (JavaDfaSliceValueFilter)myAnalysisParams.valueFilter
                                          : new JavaDfaSliceValueFilter(DfTypes.TOP);
      JavaDfaSliceValueFilter filter = curFilter.mergeFilter(realExpression);
      SliceAnalysisParams params = myAnalysisParams;
      if (filter != curFilter) {
        params = new SliceAnalysisParams(params);
        params.valueFilter = filter;
      }
      return processor.process(new JavaSliceUsage(realExpression, myParent, params, mySubstitutor, myIndexNesting, mySyntheticField));
    }
    return true;
  }

  /**
   * Update nesting level from flow annotation
   * @param anno flow annotation
   * @return slice builder with updated nesting level
   */
  @Contract(pure = true)
  @NotNull JavaSliceBuilder updateNesting(@NotNull Flow anno) {
    JavaSliceBuilder result = this;
    if (anno.targetIsContainer()) {
      result = result.decrementNesting();
    }
    if (anno.sourceIsContainer()) {
      result = result.incrementNesting();
    }
    return result;
  }

  @Contract(pure = true)
  @NotNull JavaSliceBuilder incrementNesting() {
    SliceValueFilter filter = myAnalysisParams.valueFilter;
    filter = filter instanceof JavaDfaSliceValueFilter ? ((JavaDfaSliceValueFilter)filter).wrap() : null;
    return new JavaSliceBuilder(myParent, mySubstitutor, myIndexNesting + 1, mySyntheticField, myAnalysisParams)
      .withFilter(filter);
  }

  @Contract(pure = true)
  @NotNull JavaSliceBuilder decrementNesting() {
    SliceValueFilter filter = myAnalysisParams.valueFilter;
    filter = filter instanceof JavaDfaSliceValueFilter ? ((JavaDfaSliceValueFilter)filter).unwrap() : null;
    return new JavaSliceBuilder(myParent, mySubstitutor, myIndexNesting - 1, mySyntheticField, myAnalysisParams)
      .withFilter(filter);
  }

  boolean hasNesting() {
    return myIndexNesting > 0;
  }

  @Contract(pure = true)
  @NotNull SliceUsage getParent() {
    return myParent;
  }

  @Contract(pure = true)
  @NotNull PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }
  
  PsiType substitute(@Nullable PsiType type) {
    return mySubstitutor.substitute(type);
  }

  @Contract(pure = true)
  @NotNull String getSyntheticField() {
    return mySyntheticField;
  }

  /**
   * @param parent parent usage
   * @return new JavaSliceBuilder that inherits the parent properties
   */
  @Contract(pure = true)
  static @NotNull JavaSliceBuilder create(SliceUsage parent) {
    if (parent instanceof JavaSliceUsage) {
      JavaSliceUsage javaParent = (JavaSliceUsage)parent;
      return new JavaSliceBuilder(parent, javaParent.getSubstitutor(), javaParent.indexNesting, javaParent.syntheticField, parent.params);
    }
    return new JavaSliceBuilder(parent, PsiSubstitutor.EMPTY, 0, "", parent.params);
  }

  @Nullable JavaSliceBuilder combineSubstitutor(@NotNull PsiSubstitutor substitutor, @NotNull Project project) {
    PsiSubstitutor parentSubstitutor = this.mySubstitutor;
    Map<PsiTypeParameter, PsiType> newMap = new THashMap<>(substitutor.getSubstitutionMap());

    for (Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
      PsiTypeParameter typeParameter = entry.getKey();
      PsiType type = entry.getValue();
      PsiClass resolved = PsiUtil.resolveClassInType(type);
      if (!parentSubstitutor.getSubstitutionMap().containsKey(typeParameter)) continue;
      PsiType parentType = parentSubstitutor.substitute(parentSubstitutor.substitute(typeParameter));

      if (resolved instanceof PsiTypeParameter) {
        PsiTypeParameter res = (PsiTypeParameter)resolved;
        newMap.put(res, parentType);
      }
      else if (!Comparing.equal(type, parentType)) {
        return null; // cannot unify
      }
    }
    PsiSubstitutor combined = JavaPsiFacade.getElementFactory(project).createSubstitutor(newMap);
    return withSubstitutor(combined);
  }
}
