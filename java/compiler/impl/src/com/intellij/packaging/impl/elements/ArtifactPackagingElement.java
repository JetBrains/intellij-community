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
package com.intellij.packaging.impl.elements;

import com.intellij.compiler.ant.BuildProperties;
import com.intellij.compiler.ant.Generator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointer;
import com.intellij.packaging.artifacts.ArtifactPointerManager;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.ui.ArtifactElementPresentation;
import com.intellij.packaging.impl.ui.DelegatedPackagingElementPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactPackagingElement extends ComplexPackagingElement<ArtifactPackagingElement.ArtifactPackagingElementState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.packaging.impl.elements.ArtifactPackagingElement");
  private final Project myProject;
  private ArtifactPointer myArtifactPointer;
  @NonNls public static final String ARTIFACT_NAME_ATTRIBUTE = "artifact-name";

  public ArtifactPackagingElement(@NotNull Project project) {
    super(ArtifactElementType.ARTIFACT_ELEMENT_TYPE);
    myProject = project;
  }

  public ArtifactPackagingElement(@NotNull Project project, @NotNull ArtifactPointer artifactPointer) {
    this(project);
    myArtifactPointer = artifactPointer;
  }

  public List<? extends PackagingElement<?>> getSubstitution(@NotNull PackagingElementResolvingContext context, @NotNull ArtifactType artifactType) {
    final Artifact artifact = findArtifact(context);
    if (artifact != null) {
      final ArtifactType type = artifact.getArtifactType();
      List<? extends PackagingElement<?>> substitution = type.getSubstitution(artifact, context, artifactType);
      if (substitution != null) {
        return substitution;
      }

      final List<PackagingElement<?>> elements = new ArrayList<>();
      final CompositePackagingElement<?> rootElement = artifact.getRootElement();
      if (rootElement instanceof ArtifactRootElement<?>) {
        elements.addAll(rootElement.getChildren());
      }
      else {
        elements.add(rootElement);
      }
      return elements;
    }
    return null;
  }

  @Override
  public List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext, @NotNull AntCopyInstructionCreator creator,
                                                          @NotNull ArtifactAntGenerationContext generationContext,
                                                          @NotNull ArtifactType artifactType) {
    final Artifact artifact = findArtifact(resolvingContext);
    if (artifact != null) {
      if (artifact.getArtifactType().getSubstitution(artifact, resolvingContext, artifactType) != null) {
        return super.computeAntInstructions(resolvingContext, creator, generationContext, artifactType);
      }
      final String outputPath = BuildProperties.propertyRef(generationContext.getArtifactOutputProperty(artifact));
      return Collections.singletonList(creator.createDirectoryContentCopyInstruction(outputPath));
    }
    return Collections.emptyList();
  }

  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new DelegatedPackagingElementPresentation(new ArtifactElementPresentation(myArtifactPointer, context));
  }

  public ArtifactPackagingElementState getState() {
    final ArtifactPackagingElementState state = new ArtifactPackagingElementState();
    if (myArtifactPointer != null) {
      state.setArtifactName(myArtifactPointer.getArtifactName());
    }
    return state;
  }

  public void loadState(ArtifactPackagingElementState state) {
    final String name = state.getArtifactName();
    myArtifactPointer = name != null ? ArtifactPointerManager.getInstance(myProject).createPointer(name) : null;
  }

  @Override
  public String toString() {
    return "artifact:" + getArtifactName();
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof ArtifactPackagingElement && myArtifactPointer != null
           && myArtifactPointer.equals(((ArtifactPackagingElement)element).myArtifactPointer);
  }

  @Nullable
  public Artifact findArtifact(@NotNull PackagingElementResolvingContext context) {
    return myArtifactPointer != null ? myArtifactPointer.findArtifact(context.getArtifactModel()) : null;
  }

  @Nullable
  public String getArtifactName() {
    return myArtifactPointer != null ? myArtifactPointer.getArtifactName() : null;
  }


  public static class ArtifactPackagingElementState {
    private String myArtifactName;

    @Attribute(ARTIFACT_NAME_ATTRIBUTE)
    public String getArtifactName() {
      return myArtifactName;
    }

    public void setArtifactName(String artifactName) {
      myArtifactName = artifactName;
    }
  }
}
