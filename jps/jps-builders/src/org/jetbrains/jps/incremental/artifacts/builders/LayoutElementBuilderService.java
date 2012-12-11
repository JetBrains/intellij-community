/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.artifacts.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactCompilerInstructionCreator;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactInstructionsBuilderContext;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;

import java.util.Collection;
import java.util.Collections;

/**
 * @author nik
 */
public abstract class LayoutElementBuilderService<E extends JpsPackagingElement> {
  private final Class<E> myElementClass;

  protected LayoutElementBuilderService(Class<E> elementClass) {
    myElementClass = elementClass;
  }

  public abstract void generateInstructions(E element, ArtifactCompilerInstructionCreator instructionCreator, ArtifactInstructionsBuilderContext builderContext);

  public Collection<? extends BuildTarget<?>> getDependencies(@NotNull E element, TargetOutputIndex outputIndex) {
    return Collections.emptyList();
  }

  public final Class<E> getElementClass() {
    return myElementClass;
  }
}
