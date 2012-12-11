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
package org.jetbrains.jps.model.artifact;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsNamedElement;
import org.jetbrains.jps.model.JpsReferenceableElement;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;

/**
 * @author nik
 */
public interface JpsArtifact extends JpsNamedElement, JpsReferenceableElement<JpsArtifact>, JpsCompositeElement {
  @NotNull
  JpsArtifactType<?> getArtifactType();

  @Nullable
  String getOutputPath();

  void setOutputPath(@Nullable String outputPath);

  @Nullable
  String getOutputFilePath();

  @NotNull
  JpsCompositePackagingElement getRootElement();

  void setRootElement(@NotNull JpsCompositePackagingElement rootElement);

  boolean isBuildOnMake();

  @NotNull
  @Override
  JpsArtifactReference createReference();

  void setBuildOnMake(boolean buildOnMake);

  JpsElement getProperties();
}
