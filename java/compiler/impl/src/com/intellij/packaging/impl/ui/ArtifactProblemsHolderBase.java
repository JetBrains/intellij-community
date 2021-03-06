/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.packaging.impl.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.ui.ArtifactProblemsHolder;
import org.jetbrains.annotations.NotNull;

public abstract class ArtifactProblemsHolderBase implements ArtifactProblemsHolder {
  private final PackagingElementResolvingContext myContext;

  protected ArtifactProblemsHolderBase(PackagingElementResolvingContext context) {
    myContext = context;
  }

  @Override
  @NotNull
  public PackagingElementResolvingContext getContext() {
    return myContext;
  }

  @Override
  public void registerError(@NotNull @NlsContexts.DialogMessage String message, @NotNull String problemTypeId) {
    registerError(message, problemTypeId, null);
  }
}
