// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A language extension point that builds {@link ControlFlow} for specific language
 */
public interface DataFlowIRProvider {
  LanguageExtension<DataFlowIRProvider> EP_NAME = new LanguageExtension<>("com.intellij.dataflowIRProvider");

  /**
   * Create control flow for given PSI block (method body, lambda expression, etc.) and return it.
   * It's prohibited to change the resulting control flow (e.g. add instructions, update their indices, update flush variable lists, etc.)
   *
   * @param psiBlock psi-block
   * @param factory factory to bind the PSI block to
   * @return resulting control flow; null if it cannot be built (e.g. if the code block contains unrecoverable errors)
   */
  @Nullable ControlFlow createControlFlow(@NotNull DfaValueFactory factory, @NotNull PsiElement psiBlock);

  /**
   * @param startAnchor         an anchor returned from {@link DfaAssistProvider#getAnchor(PsiElement)}, which created this listener
   * @param unreachableElements list of all {@link PsiElement} which were never visited during IR interpretation. This means that no
   *                            instruction between {@link ControlFlow#startElement(PsiElement)} and
   *                            {@link ControlFlow#finishElement(PsiElement)} was reached. Elements before startAnchor in IR are not
   *                            considered unreachable and not added to this set.
   * @return collection of text ranges to highlight as unreachable.
   */
  default @NotNull Collection<TextRange> unreachableSegments(@NotNull PsiElement startAnchor, @NotNull Set<PsiElement> unreachableElements) {
    return Collections.emptyList();
  }

  /**
   * Create control flow for given PSI block (method body, lambda expression, etc.) and return it. May return cached block.
   * It's prohibited to change the resulting control flow (e.g. add instructions, update their indices, update flush variable lists, etc.)
   *
   * @param psiBlock psi-block
   * @param targetFactory factory to bind the PSI block to
   * @return resulting control flow; null if it cannot be built (e.g. unsupported language, code block contains unrecoverable errors, etc.)
   */
  static @Nullable ControlFlow forElement(@NotNull PsiElement psiBlock, @NotNull DfaValueFactory targetFactory) {
    PsiFile file = psiBlock.getContainingFile();
    DataFlowIRProvider provider = EP_NAME.forLanguage(file.getLanguage());
    if (provider == null) {
      return null;
    }
    ConcurrentHashMap<PsiElement, Optional<ControlFlow>> fileMap =
      CachedValuesManager.getCachedValue(file, () ->
        CachedValueProvider.Result.create(new ConcurrentHashMap<>(), PsiModificationTracker.MODIFICATION_COUNT));
    return fileMap.computeIfAbsent(psiBlock, psi -> {
      DfaValueFactory factory = new DfaValueFactory(file.getProject());
      ControlFlow flow = provider.createControlFlow(factory, psiBlock);
      return Optional.ofNullable(flow);
    }).map(flow -> new ControlFlow(flow, targetFactory)).orElse(null);
  }
  
  static @NotNull Collection<TextRange> computeUnreachableSegments(@NotNull PsiElement startAnchor, @NotNull Set<PsiElement> unreachableElements) {
    PsiFile file = startAnchor.getContainingFile();
    DataFlowIRProvider provider = EP_NAME.forLanguage(file.getLanguage());
    if (provider == null) {
      return Collections.emptyList();
    }
    return provider.unreachableSegments(startAnchor, unreachableElements);
  }
}
