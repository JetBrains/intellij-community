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
import com.intellij.compiler.chainsSearch.OccurrencesAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.LightRef;

import java.util.SortedSet;

/**
 * The service is used for java relevant chain completion
 */
public abstract class CompilerReferenceServiceEx extends CompilerReferenceService {
  protected CompilerReferenceServiceEx(Project project) {
    super(project);
  }

  @NotNull
  public abstract SortedSet<OccurrencesAware<MethodIncompleteSignature>> findMethodReferenceOccurrences(@NotNull String rawReturnType)
    throws ReferenceIndexUnavailableException;

  public abstract boolean mayHappen(@NotNull LightRef qualifier, @NotNull LightRef base, int probabilityThreshold)
    throws ReferenceIndexUnavailableException;

  @NotNull
  public abstract String getName(int idx)
    throws ReferenceIndexUnavailableException;
}
