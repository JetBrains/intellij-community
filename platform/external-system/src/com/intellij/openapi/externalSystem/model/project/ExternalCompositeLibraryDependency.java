/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.roots.LibraryOrderEntry;
import org.jetbrains.annotations.NotNull;

/**
 * Stands for an entity which references two library dependencies.
 * 
 * @author Denis Zhdanov
 * @since 1/22/13 5:28 PM
 */
public class ExternalCompositeLibraryDependency extends AbstractExternalCompositeEntity<ExternalLibraryDependency, LibraryOrderEntry> {

  public ExternalCompositeLibraryDependency(@NotNull ExternalLibraryDependency externalLibraryDependency,
                                            @NotNull LibraryOrderEntry ideLibraryDependency)
  {
    super(externalLibraryDependency, ideLibraryDependency);
  }

  @Override
  public void invite(@NotNull ExternalEntityVisitor visitor) {
    visitor.visit(this);
  }

  @NotNull
  @Override
  public ExternalEntity clone(@NotNull ExternalEntityCloneContext context) {
    return new ExternalCompositeLibraryDependency(getExternalEntity(), getIdeEntity());
  }

  

  @Override
  public String toString() {
    return String.format("composite library dependency - external: %s, ide: %s", getExternalEntity(), getIdeEntity());
  }
}
