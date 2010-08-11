/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.compiler.generic;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class GenericCompilerCacheState<Key, SourceState, OutputState> {
  private final Key myKey;
  private final SourceState mySourceState;
  private final OutputState myOutputState;

  public GenericCompilerCacheState(@NotNull Key key, @NotNull SourceState sourceState, @NotNull OutputState outputState) {
    myKey = key;
    mySourceState = sourceState;
    myOutputState = outputState;
  }

  @NotNull
  public Key getKey() {
    return myKey;
  }

  @NotNull
  public SourceState getSourceState() {
    return mySourceState;
  }

  @NotNull
  public OutputState getOutputState() {
    return myOutputState;
  }
}
