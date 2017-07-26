/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.backwardRefs.index;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.backwardRefs.LightRef;
import org.jetbrains.backwardRefs.SignatureData;

import java.util.Collection;
import java.util.Map;

public class CompiledFileData {
  private final Map<LightRef, Collection<LightRef>> myBackwardHierarchyMap;
  private final Map<LightRef, Integer> myReferences;
  private final Map<LightRef, Void> myDefinitions;
  private final Map<SignatureData, Collection<LightRef>> mySignatureData;

  public CompiledFileData(@NotNull Map<LightRef, Collection<LightRef>> backwardHierarchyMap,
                          @NotNull Map<LightRef, Integer> references,
                          @NotNull Map<LightRef, Void> definitions,
                          @NotNull Map<SignatureData, Collection<LightRef>> signatureData) {
    myBackwardHierarchyMap = backwardHierarchyMap;
    myReferences = references;
    myDefinitions = definitions;
    mySignatureData = signatureData;
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
}
