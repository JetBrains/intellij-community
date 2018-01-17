/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
