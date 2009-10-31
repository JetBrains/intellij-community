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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
public abstract class PackagingElementType<E extends PackagingElement<?>> {
  public static final ExtensionPointName<PackagingElementType> EP_NAME = ExtensionPointName.create("com.intellij.packaging.elementType");
  private final String myId;
  private final String myPresentableName;

  protected PackagingElementType(@NotNull @NonNls String id, @NotNull String presentableName) {
    myId = id;
    myPresentableName = presentableName;
  }

  public final String getId() {
    return myId;
  }

  public String getPresentableName() {
    return myPresentableName;
  }

  @Nullable
  public Icon getCreateElementIcon() {
    return null;
  }

  public abstract boolean canCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact);

  @NotNull 
  public abstract List<? extends PackagingElement<?>> chooseAndCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact,
                                                                      @NotNull CompositePackagingElement<?> parent);

  @NotNull
  public abstract E createEmpty(@NotNull Project project);

  protected static <T extends PackagingElementType<?>> T getInstance(final Class<T> aClass) {
    for (PackagingElementType type : Extensions.getExtensions(EP_NAME)) {
      if (aClass.isInstance(type)) {
        return aClass.cast(type);
      }
    }
    throw new AssertionError();
  }

  @Nullable
  public PackagingElementPropertiesPanel createElementPropertiesPanel(@NotNull E element, @NotNull ArtifactEditorContext context) {
    return null;
  }
}
