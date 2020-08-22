// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import gnu.trove.THashMap;
import org.intellij.lang.annotations.Flow;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * A helper class to build new {@link JavaSliceUsage} and pass it to the processor
 */
final class JavaSliceBuilder {
  private final @NotNull SliceUsage myParent;
  private final @NotNull PsiSubstitutor mySubstitutor;
  private final @Range(from = 0, to = Integer.MAX_VALUE) int myIndexNesting;
  private final @NotNull String mySyntheticField;
  private final @NotNull JavaValueFilter myFilter;

  private JavaSliceBuilder(@NotNull SliceUsage parent,
                           @NotNull PsiSubstitutor substitutor,
                           @Range(from = 0, to = Integer.MAX_VALUE) int indexNesting,
                           @NotNull String syntheticField,
                           @Nullable SliceValueFilter filter) {
    assert indexNesting >= 0 : indexNesting;
    myParent = parent;
    mySubstitutor = substitutor;
    myIndexNesting = indexNesting;
    mySyntheticField = syntheticField;
    myFilter = filter instanceof JavaValueFilter
               ? (JavaValueFilter)filter
               : JavaValueFilter.ALLOW_EVERYTHING;
  }

  @Contract(pure = true)
  @NotNull JavaSliceBuilder withSubstitutor(@NotNull PsiSubstitutor substitutor) {
    return new JavaSliceBuilder(myParent, substitutor, myIndexNesting, mySyntheticField, myFilter);
  }

  @Contract(pure = true)
  @NotNull JavaSliceBuilder withSyntheticField(@NotNull String syntheticField) {
    if (syntheticField.equals(mySyntheticField)) return this;
    return new JavaSliceBuilder(myParent, mySubstitutor, myIndexNesting, syntheticField, myFilter);
  }

  @Contract(pure = true)
  @NotNull JavaSliceBuilder dropSyntheticField() {
    return withSyntheticField("");
  }

  @Contract(pure = true)
  @NotNull JavaSliceBuilder dropNesting() {
    if (myIndexNesting == 0) return this;
    return new JavaSliceBuilder(myParent, mySubstitutor, 0, mySyntheticField, myFilter.withType(DfTypes.TOP));
  }

  boolean process(PsiElement element, Processor<? super SliceUsage> processor) {
    final PsiElement realExpression = element.getParent() instanceof DummyHolder ? element.getParent().getContext() : element;
    assert realExpression != null;
    if (!(realExpression instanceof PsiCompiledElement)) {
      JavaValueFilter filter = myFilter.mergeFilter(realExpression);
      SliceAnalysisParams params = myParent.params;
      if (filter != params.valueFilter) {
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
    if (anno.targetIsContainer() && anno.sourceIsContainer()) {
      return new JavaSliceBuilder(myParent, mySubstitutor, myIndexNesting, mySyntheticField, myFilter.unwrap().wrap());
    }
    if (anno.targetIsContainer()) {
      return decrementNesting();
    }
    if (anno.sourceIsContainer()) {
      return incrementNesting();
    }
    return this;
  }

  @Contract(pure = true)
  @NotNull JavaSliceBuilder withFilter(UnaryOperator<JavaValueFilter> filterTransformer) {
    JavaValueFilter filter = filterTransformer.apply(myFilter);
    if (filter == myFilter) return this;
    return new JavaSliceBuilder(myParent, mySubstitutor, myIndexNesting, mySyntheticField, filter);
  }

  @NotNull SearchScope getSearchScope() {
    AnalysisScope scope = myParent.getScope();
    return myFilter.correctScope(scope.toSearchScope());
  }

  @Contract(pure = true)
  @NotNull JavaSliceBuilder incrementNesting() {
    return new JavaSliceBuilder(myParent, mySubstitutor, myIndexNesting + 1, mySyntheticField, myFilter.wrap());
  }

  @Contract(pure = true)
  @NotNull JavaSliceBuilder decrementNesting() {
    return new JavaSliceBuilder(myParent, mySubstitutor, myIndexNesting - 1, mySyntheticField, myFilter.unwrap());
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
  
  @Contract(pure = true)
  PsiType substitute(@Nullable PsiType type) {
    return mySubstitutor.substitute(type);
  }

  @Contract(pure = true)
  @NotNull String getSyntheticField() {
    return mySyntheticField;
  }

  @Contract(pure = true)
  @NotNull JavaValueFilter getFilter() {
    return myFilter;
  }

  /**
   * @param parent parent usage
   * @return new JavaSliceBuilder that inherits the parent properties
   */
  @Contract(pure = true)
  static @NotNull JavaSliceBuilder create(@NotNull SliceUsage parent) {
    SliceValueFilter filter = parent.params.valueFilter;
    if (parent instanceof JavaSliceUsage) {
      JavaSliceUsage javaParent = (JavaSliceUsage)parent;
      return new JavaSliceBuilder(parent, javaParent.getSubstitutor(), javaParent.indexNesting, javaParent.syntheticField, filter);
    }
    return new JavaSliceBuilder(parent, PsiSubstitutor.EMPTY, 0, "", filter);
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
