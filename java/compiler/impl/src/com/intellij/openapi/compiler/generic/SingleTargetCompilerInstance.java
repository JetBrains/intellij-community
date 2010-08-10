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

import com.intellij.openapi.compiler.CompileContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class SingleTargetCompilerInstance<Item extends CompileItem<K,S,O>, K,S, O> extends
                                                                                            GenericCompilerInstance<BuildTarget, Item, K, S, O> {
  protected SingleTargetCompilerInstance(CompileContext context) {
    super(context);
  }

  @NotNull
  @Override
  public List<BuildTarget> getAllTargets() {
    return Collections.singletonList(BuildTarget.DEFAULT);
  }

  @NotNull
  @Override
  public List<BuildTarget> getSelectedTargets() {
    return getAllTargets();
  }

  @Override
  public void processObsoleteTarget(@NotNull String targetId, @NotNull List<GenericCompilerCacheState<K, S, O>> obsoleteItems) {
  }
}
