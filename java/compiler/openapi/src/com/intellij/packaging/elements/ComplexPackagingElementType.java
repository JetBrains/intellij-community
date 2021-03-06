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

package com.intellij.packaging.elements;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public abstract class ComplexPackagingElementType<E extends ComplexPackagingElement<?>> extends PackagingElementType<E> {
  protected ComplexPackagingElementType(@NotNull @NonNls String id,
                                        @NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Title) String> presentableName) {
    super(id, presentableName);
  }

  public abstract @NlsActions.ActionText String getShowContentActionText();

  @Nullable
  public ModificationTracker getAllSubstitutionsModificationTracker(@NotNull Project project) {
    return null;
  }
}
