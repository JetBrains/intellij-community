// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs.index;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.CompilerRef;
import org.jetbrains.jps.backwardRefs.SignatureData;

import java.util.Collection;
import java.util.Map;

public class CompiledFileData {
  private final Map<CompilerRef, Collection<CompilerRef>> myBackwardHierarchyMap;
  private final Map<CompilerRef, Collection<CompilerRef>> myCasts;
  private final Map<CompilerRef, Integer> myReferences;
  private final Map<CompilerRef, Void> myDefinitions;
  private final Map<SignatureData, Collection<CompilerRef>> mySignatureData;
  private final Map<CompilerRef, Void> myImplicitToString;

  public CompiledFileData(@NotNull Map<CompilerRef, Collection<CompilerRef>> backwardHierarchyMap,
                          @NotNull Map<CompilerRef, Collection<CompilerRef>> casts,
                          @NotNull Map<CompilerRef, Integer> references,
                          @NotNull Map<CompilerRef, Void> definitions,
                          @NotNull Map<SignatureData, Collection<CompilerRef>> signatureData,
                          @NotNull Map<CompilerRef, Void> implicitToString) {
    myBackwardHierarchyMap = backwardHierarchyMap;
    myCasts = casts;
    myReferences = references;
    myDefinitions = definitions;
    mySignatureData = signatureData;
    myImplicitToString = implicitToString;
  }

  @NotNull
  public Map<CompilerRef, Collection<CompilerRef>> getBackwardHierarchy() {
    return myBackwardHierarchyMap;
  }

  @NotNull
  public Map<CompilerRef, Integer> getReferences() {
    return myReferences;
  }

  @NotNull
  public Map<CompilerRef, Void> getDefinitions() {
    return myDefinitions;
  }

  @NotNull
  public Map<SignatureData, Collection<CompilerRef>> getSignatureData() {
    return mySignatureData;
  }

  @NotNull
  public Map<CompilerRef, Collection<CompilerRef>> getCasts() {
    return myCasts;
  }

  @NotNull
  public Map<CompilerRef, Void> getImplicitToString() {
    return myImplicitToString;
  }
}
