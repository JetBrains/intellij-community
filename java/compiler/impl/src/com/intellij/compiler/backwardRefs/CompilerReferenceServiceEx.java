// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.backwardRefs;

import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.chainsSearch.ChainOpAndOccurrences;
import com.intellij.compiler.chainsSearch.MethodCall;
import com.intellij.compiler.chainsSearch.TypeCast;
import com.intellij.compiler.chainsSearch.context.ChainCompletionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.LightRef;
import org.jetbrains.jps.backwardRefs.SignatureData;

import java.util.SortedSet;

/**
 * The service is used for / java completion sorting / java relevant chain completion / frequently used superclass inspection
 */
public interface CompilerReferenceServiceEx extends CompilerReferenceService {
  @NotNull
  SortedSet<ChainOpAndOccurrences<MethodCall>> findMethodReferenceOccurrences(@NotNull String rawReturnType,
                                                                              @SignatureData.IteratorKind byte iteratorKind,
                                                                              @NotNull ChainCompletionContext context)
    throws ReferenceIndexUnavailableException;

  @Nullable
  ChainOpAndOccurrences<TypeCast> getMostUsedTypeCast(@NotNull String operandQName)
    throws ReferenceIndexUnavailableException;

  @Nullable
  LightRef.LightClassHierarchyElementDef mayCallOfTypeCast(@NotNull LightRef.JavaLightMethodRef method, int probabilityThreshold)
    throws ReferenceIndexUnavailableException;

  boolean mayHappen(@NotNull LightRef qualifier, @NotNull LightRef base, int probabilityThreshold)
    throws ReferenceIndexUnavailableException;

  @NotNull
  String getName(int idx)
    throws ReferenceIndexUnavailableException;

  int getNameId(@NotNull String name) throws ReferenceIndexUnavailableException;

  @NotNull
  LightRef.LightClassHierarchyElementDef[] getDirectInheritors(LightRef.LightClassHierarchyElementDef baseClass)
    throws ReferenceIndexUnavailableException;

  int getInheritorCount(LightRef.LightClassHierarchyElementDef baseClass) throws ReferenceIndexUnavailableException;
}
