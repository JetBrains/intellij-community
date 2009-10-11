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
package com.intellij.packaging.impl.artifacts;

import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.elements.ArchivePackagingElement;
import com.intellij.packaging.ui.PackagingSourceItem;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class JarArtifactType extends ArtifactType {
  public JarArtifactType() {
    super("jar", "Jar");
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return PlainArtifactType.ARTIFACT_ICON;
  }

  @Override
  public String getDefaultPathFor(@NotNull PackagingSourceItem sourceItem) {
    return "/";
  }

  @Override
  public String getDefaultPathFor(@NotNull PackagingElement<?> element, @NotNull PackagingElementResolvingContext context) {
    return "/";
  }

  @NotNull
  @Override
  public CompositePackagingElement<?> createRootElement(@NotNull String artifactName) {
    return new ArchivePackagingElement(artifactName + ".jar");
  }
}
