/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.elements.ArtifactElementType;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class ArtifactValidationUtilImpl extends ArtifactValidationUtil {
  private Project myProject;
  private CachedValue<Set<Artifact>> mySelfIncludingArtifacts;

  public ArtifactValidationUtilImpl(Project project) {
    myProject = project;
  }

  @Override
  public Set<Artifact> getSelfIncludingArtifacts() {
    if (mySelfIncludingArtifacts == null) {
      mySelfIncludingArtifacts = CachedValuesManager.getManager(myProject).createCachedValue(new CachedValueProvider<Set<Artifact>>() {
        public Result<Set<Artifact>> compute() {
          return Result.create(computeSelfIncludingArtifacts(), ArtifactManager.getInstance(myProject).getModificationTracker());
        }
      }, false);
    }
    return mySelfIncludingArtifacts.getValue();
  }

  private Set<Artifact> computeSelfIncludingArtifacts() {
    final ArtifactManager artifactManager = ArtifactManager.getInstance(myProject);
    Set<Artifact> result = new HashSet<Artifact>();
    final PackagingElementResolvingContext context = artifactManager.getResolvingContext();
    for (final Artifact artifact : artifactManager.getSortedArtifacts()) {
      if (!ArtifactUtil.processPackagingElements(artifact, ArtifactElementType.ARTIFACT_ELEMENT_TYPE,
                                                 new PackagingElementProcessor<ArtifactPackagingElement>() {
                                                   @Override
                                                   public boolean process(@NotNull ArtifactPackagingElement element,
                                                                          @NotNull PackagingElementPath path) {
                                                     return !artifact.equals(element.findArtifact(context));
                                                   }
                                                 }, context, true)) {
        result.add(artifact);
      }
    }
    return result;
  }
}
