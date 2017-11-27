// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs.index;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.LightRef;
import org.jetbrains.jps.backwardRefs.SignatureData;

import java.util.Collection;
import java.util.Map;

public class CompiledFileData {
  private final Map<LightRef, Collection<LightRef>> myBackwardHierarchyMap;
  private final Map<LightRef, Collection<LightRef>> myCasts;
  private final Map<LightRef, Integer> myReferences;
  private final Map<LightRef, Void> myDefinitions;
  private final Map<SignatureData, Collection<LightRef>> mySignatureData;
  private final Map<LightRef, Void> myImplicitToString;

  public CompiledFileData(@NotNull Map<LightRef, Collection<LightRef>> backwardHierarchyMap,
                          @NotNull Map<LightRef, Collection<LightRef>> casts,
                          @NotNull Map<LightRef, Integer> references,
                          @NotNull Map<LightRef, Void> definitions,
                          @NotNull Map<SignatureData, Collection<LightRef>> signatureData,
                          @NotNull Map<LightRef, Void> implicitToString) {
    myBackwardHierarchyMap = backwardHierarchyMap;
    myCasts = casts;
    myReferences = references;
    myDefinitions = definitions;
    mySignatureData = signatureData;
    myImplicitToString = implicitToString;
  }

  @NotNull
  public Map<LightRef, Collection<LightRef>> getBackwardHierarchy() {
    return myBackwardHierarchyMap;
  }

  @NotNull
  public Map<LightRef, Integer> getReferences() {
    return myReferences;
  }

  @NotNull
  public Map<LightRef, Void> getDefinitions() {
    return myDefinitions;
  }

  @NotNull
  public Map<SignatureData, Collection<LightRef>> getSignatureData() {
    return mySignatureData;
  }

  @NotNull
  public Map<LightRef, Collection<LightRef>> getCasts() {
    return myCasts;
  }

  @NotNull
  public Map<LightRef, Void> getImplicitToString() {
    return myImplicitToString;
  }
}
