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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public class ArtifactValidationUtilImpl extends ArtifactValidationUtil {
  private final Project myProject;
  private CachedValue<Map<Artifact, String>> myArtifactToSelfIncludingName;

  public ArtifactValidationUtilImpl(Project project) {
    myProject = project;
  }

  @Override
  public Map<Artifact, String> getArtifactToSelfIncludingNameMap() {
    if (myArtifactToSelfIncludingName == null) {
      myArtifactToSelfIncludingName = CachedValuesManager.getManager(myProject).createCachedValue(new CachedValueProvider<Map<Artifact, String>>() {
        public Result<Map<Artifact, String>> compute() {
          return Result.create(computeArtifactToSelfIncludingNameMap(), ArtifactManager.getInstance(myProject).getModificationTracker());
        }
      }, false);
    }
    return myArtifactToSelfIncludingName.getValue();
  }

  private Map<Artifact, String> computeArtifactToSelfIncludingNameMap() {
    final Map<Artifact, String> result = new HashMap<Artifact, String>();
    final ArtifactManager artifactManager = ArtifactManager.getInstance(myProject);
    final GraphGenerator<Artifact> graph = GraphGenerator.create(CachingSemiGraph.create(new ArtifactsGraph(artifactManager)));
    for (Artifact artifact : graph.getNodes()) {
      final Iterator<Artifact> in = graph.getIn(artifact);
      while (in.hasNext()) {
        Artifact next = in.next();
        if (next.equals(artifact)) {
          result.put(artifact, artifact.getName());
          break;
        }
      }
    }

    final DFSTBuilder<Artifact> builder = new DFSTBuilder<Artifact>(graph);
    builder.buildDFST();
    if (builder.isAcyclic() && result.isEmpty()) return Collections.emptyMap();

    final TIntArrayList sccs = builder.getSCCs();
    sccs.forEach(new TIntProcedure() {
      int myTNumber = 0;
      public boolean execute(int size) {
        if (size > 1) {
          for (int j = 0; j < size; j++) {
            final Artifact artifact = builder.getNodeByTNumber(myTNumber + j);
            result.put(artifact, artifact.getName());
          }
        }
        myTNumber += size;
        return true;
      }
    });

    for (int i = 0; i < graph.getNodes().size(); i++) {
      final Artifact artifact = builder.getNodeByTNumber(i);
      if (!result.containsKey(artifact)) {
        final Iterator<Artifact> in = graph.getIn(artifact);
        while (in.hasNext()) {
          final String name = result.get(in.next());
          if (name != null) {
            result.put(artifact, name);
          }
        }
      }
    }

    return result;
  }

  private class ArtifactsGraph implements GraphGenerator.SemiGraph<Artifact> {
    private final ArtifactManager myArtifactManager;

    public ArtifactsGraph(ArtifactManager artifactManager) {
      myArtifactManager = artifactManager;
    }

    @Override
    public Collection<Artifact> getNodes() {
      return Arrays.asList(myArtifactManager.getSortedArtifacts());
    }

    @Override
    public Iterator<Artifact> getIn(Artifact n) {
      final Set<Artifact> included = new LinkedHashSet<Artifact>();
      final PackagingElementResolvingContext context = myArtifactManager.getResolvingContext();
      ArtifactUtil.processPackagingElements(n, ArtifactElementType.ARTIFACT_ELEMENT_TYPE, new PackagingElementProcessor<ArtifactPackagingElement>() {
        @Override
        public boolean process(@NotNull ArtifactPackagingElement element,
                               @NotNull PackagingElementPath path) {
          ContainerUtil.addIfNotNull(included, element.findArtifact(context));
          return true;
        }
      }, context, false);
      return included.iterator();
    }
  }

}
