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
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.facet.Facet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.*;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.PackagingElementPath;
import com.intellij.packaging.impl.artifacts.PackagingElementProcessor;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;
import com.intellij.packaging.impl.elements.FacetBasedPackagingElement;
import com.intellij.packaging.impl.elements.LibraryPackagingElement;
import com.intellij.packaging.impl.elements.ModuleOutputPackagingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactProjectStructureElement extends ProjectStructureElement {
  private final ArtifactsStructureConfigurableContext myArtifactsStructureContext;
  private final Artifact myOriginalArtifact;

  ArtifactProjectStructureElement(StructureConfigurableContext context,
                                  ArtifactsStructureConfigurableContext artifactsStructureContext, Artifact artifact) {
    super(context);
    myArtifactsStructureContext = artifactsStructureContext;
    myOriginalArtifact = artifactsStructureContext.getOriginalArtifact(artifact);
  }

  @Override
  public void check(final ProjectStructureProblemsHolder problemsHolder) {
    final Artifact artifact = myArtifactsStructureContext.getArtifactModel().getArtifactByOriginal(myOriginalArtifact);
    final ArtifactProblemsHolderImpl artifactProblemsHolder = new ArtifactProblemsHolderImpl(myArtifactsStructureContext, myOriginalArtifact, problemsHolder);
    artifact.getArtifactType().checkRootElement(myArtifactsStructureContext.getRootElement(myOriginalArtifact), artifact, artifactProblemsHolder);
  }

  public Artifact getOriginalArtifact() {
    return myOriginalArtifact;
  }

  @Override
  public List<ProjectStructureElementUsage> getUsagesInElement() {
    final Artifact artifact = myArtifactsStructureContext.getArtifactModel().getArtifactByOriginal(myOriginalArtifact);
    final List<ProjectStructureElementUsage> usages = new ArrayList<>();
    final CompositePackagingElement<?> rootElement = myArtifactsStructureContext.getRootElement(artifact);
    ArtifactUtil.processPackagingElements(rootElement, null, new PackagingElementProcessor<PackagingElement<?>>() {
      @Override
      public boolean process(@NotNull PackagingElement<?> packagingElement, @NotNull PackagingElementPath path) {
        ProjectStructureElement element = getProjectStructureElementFor(packagingElement, ArtifactProjectStructureElement.this.myContext,
                                                                        ArtifactProjectStructureElement.this.myArtifactsStructureContext);
        if (element != null) {
          usages.add(createUsage(packagingElement, element, path.getPathStringFrom("/", rootElement)));
        }
        return true;
      }
    }, myArtifactsStructureContext, false, artifact.getArtifactType());
    return usages;
  }

  @Nullable
  public static ProjectStructureElement getProjectStructureElementFor(PackagingElement<?> packagingElement,
                                                                       final StructureConfigurableContext context,
                                                                       final ArtifactsStructureConfigurableContext artifactsStructureContext) {
    if (packagingElement instanceof ModuleOutputPackagingElement) {
      final Module module = ((ModuleOutputPackagingElement)packagingElement).findModule(artifactsStructureContext);
      if (module != null) {
        return new ModuleProjectStructureElement(context, module);
      }
    }
    else if (packagingElement instanceof LibraryPackagingElement) {
      final Library library = ((LibraryPackagingElement)packagingElement).findLibrary(artifactsStructureContext);
      if (library != null) {
        return new LibraryProjectStructureElement(context, library);
      }
    }
    else if (packagingElement instanceof ArtifactPackagingElement) {
      final Artifact usedArtifact = ((ArtifactPackagingElement)packagingElement).findArtifact(artifactsStructureContext);
      if (usedArtifact != null) {
        return artifactsStructureContext.getOrCreateArtifactElement(usedArtifact);
      }
    }
    else if (packagingElement instanceof FacetBasedPackagingElement) {
      Facet facet = ((FacetBasedPackagingElement)packagingElement).findFacet(artifactsStructureContext);
      if (facet != null) {
        return new FacetProjectStructureElement(context, facet);
      }
    }
    return null;
  }

  private UsageInArtifact createUsage(PackagingElement<?> packagingElement, final ProjectStructureElement element, final String parentPath) {
    return new UsageInArtifact(myOriginalArtifact, myArtifactsStructureContext, element, this, parentPath, packagingElement);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ArtifactProjectStructureElement)) return false;

    return myOriginalArtifact.equals(((ArtifactProjectStructureElement)o).myOriginalArtifact);

  }

  @Override
  public int hashCode() {
    return myOriginalArtifact.hashCode();
  }

  @Override
  public String getPresentableName() {
    return getActualArtifactName();
  }

  @Override
  public String getTypeName() {
    return "Artifact";
  }

  @Override
  public String getId() {
    return "artifact:" + getActualArtifactName();
  }

  private String getActualArtifactName() {
    return myArtifactsStructureContext.getArtifactModel().getArtifactByOriginal(myOriginalArtifact).getName();
  }
}
