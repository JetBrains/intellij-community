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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.*;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.PackagingElementProcessor;
import com.intellij.packaging.impl.elements.LibraryPackagingElement;
import com.intellij.packaging.impl.elements.ModuleOutputPackagingElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactProjectStructureElement extends ProjectStructureElement {
  private final ArtifactsStructureConfigurableContext myArtifactsStructureContext;
  private final Artifact myOriginalArtifact;

  public ArtifactProjectStructureElement(StructureConfigurableContext context,
                                         ArtifactsStructureConfigurableContext artifactsStructureContext, Artifact originalArtifact) {
    super(context);
    myArtifactsStructureContext = artifactsStructureContext;
    myOriginalArtifact = originalArtifact;
  }

  @Override
  public void check(ProjectStructureProblemsHolder problemsHolder) {
  }

  @Override
  public List<ProjectStructureElementUsage> getUsagesInElement() {
    final Artifact artifact = myArtifactsStructureContext.getArtifactModel().getArtifactByOriginal(myOriginalArtifact);
    final List<ProjectStructureElementUsage> usages = new ArrayList<ProjectStructureElementUsage>();
    ArtifactUtil.processPackagingElements(myArtifactsStructureContext.getRootElement(artifact), null, new PackagingElementProcessor<PackagingElement<?>>() {
      @Override
      public boolean process(@NotNull List<CompositePackagingElement<?>> parents, @NotNull PackagingElement<?> packagingElement) {
        if (packagingElement instanceof ModuleOutputPackagingElement) {
          final Module module = ((ModuleOutputPackagingElement)packagingElement).findModule(myArtifactsStructureContext);
          if (module != null) {
            usages.add(new UsageInArtifact(myOriginalArtifact, myArtifactsStructureContext, new ModuleProjectStructureElement(myContext, module),
                                           ArtifactProjectStructureElement.this, getPathFromRoot(parents, "/"), packagingElement));
          }
        }
        else if (packagingElement instanceof LibraryPackagingElement) {
          final Library library = ((LibraryPackagingElement)packagingElement).findLibrary(myArtifactsStructureContext);
          if (library != null) {
            usages.add(new UsageInArtifact(myOriginalArtifact, myArtifactsStructureContext, new LibraryProjectStructureElement(myContext, library),
                                           ArtifactProjectStructureElement.this, getPathFromRoot(parents, "/"), packagingElement));
          }
        }
        return true;
      }
    }, myArtifactsStructureContext, false, artifact.getArtifactType());
    return usages;
  }

  @Override
  public String toString() {
    return "artifact:" + myOriginalArtifact.getName();
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
  public boolean highlightIfUnused() {
    return false;
  }
}
