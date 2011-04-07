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
public class ArtifactSortingUtilImpl extends ArtifactSortingUtil {
  private final Project myProject;
  private CachedValue<Map<String, String>> myArtifactToSelfIncludingName;
  private CachedValue<List<String>> mySortedArtifacts;

  public ArtifactSortingUtilImpl(Project project) {
    myProject = project;
  }

  @Override
  public Map<String, String> getArtifactToSelfIncludingNameMap() {
    if (myArtifactToSelfIncludingName == null) {
      myArtifactToSelfIncludingName = CachedValuesManager.getManager(myProject).createCachedValue(new CachedValueProvider<Map<String, String>>() {
        public Result<Map<String, String>> compute() {
          return Result.create(computeArtifactToSelfIncludingNameMap(), ArtifactManager.getInstance(myProject).getModificationTracker());
        }
      }, false);
    }
    return myArtifactToSelfIncludingName.getValue();
  }

  @Override
  public List<String> getArtifactsSortedByInclusion() {
    if (mySortedArtifacts == null) {
      mySortedArtifacts = CachedValuesManager.getManager(myProject).createCachedValue(new CachedValueProvider<List<String>>() {
          @Override
          public Result<List<String>> compute() {
            return Result.create(doGetSortedArtifacts(), ArtifactManager.getInstance(myProject).getModificationTracker());
          }
        }, false);
    }
    return mySortedArtifacts.getValue();
  }

  private List<String> doGetSortedArtifacts() {
    GraphGenerator<String> graph = createArtifactsGraph();
    DFSTBuilder<String> builder = new DFSTBuilder<String>(graph);
    builder.buildDFST();
    List<String> names = new ArrayList<String>();
    names.addAll(graph.getNodes());
    Collections.sort(names, builder.comparator());
    return names;
  }

  private Map<String, String> computeArtifactToSelfIncludingNameMap() {
    final Map<String, String> result = new HashMap<String, String>();
    final GraphGenerator<String> graph = createArtifactsGraph();
    for (String artifactName : graph.getNodes()) {
      final Iterator<String> in = graph.getIn(artifactName);
      while (in.hasNext()) {
        String next = in.next();
        if (next.equals(artifactName)) {
          result.put(artifactName, artifactName);
          break;
        }
      }
    }

    final DFSTBuilder<String> builder = new DFSTBuilder<String>(graph);
    builder.buildDFST();
    if (builder.isAcyclic() && result.isEmpty()) return Collections.emptyMap();

    final TIntArrayList sccs = builder.getSCCs();
    sccs.forEach(new TIntProcedure() {
      int myTNumber = 0;
      public boolean execute(int size) {
        if (size > 1) {
          for (int j = 0; j < size; j++) {
            final String artifactName = builder.getNodeByTNumber(myTNumber + j);
            result.put(artifactName, artifactName);
          }
        }
        myTNumber += size;
        return true;
      }
    });

    for (int i = 0; i < graph.getNodes().size(); i++) {
      final String artifactName = builder.getNodeByTNumber(i);
      if (!result.containsKey(artifactName)) {
        final Iterator<String> in = graph.getIn(artifactName);
        while (in.hasNext()) {
          final String name = result.get(in.next());
          if (name != null) {
            result.put(artifactName, name);
          }
        }
      }
    }

    return result;
  }

  private GraphGenerator<String> createArtifactsGraph() {
    final ArtifactManager artifactManager = ArtifactManager.getInstance(myProject);
    return GraphGenerator.create(CachingSemiGraph.create(new ArtifactsGraph(artifactManager)));
  }

  private class ArtifactsGraph implements GraphGenerator.SemiGraph<String> {
    private final ArtifactManager myArtifactManager;
    private final Set<String> myArtifactNames;

    public ArtifactsGraph(ArtifactManager artifactManager) {
      myArtifactManager = artifactManager;
      myArtifactNames = new LinkedHashSet<String>();
      for (Artifact artifact : myArtifactManager.getSortedArtifacts()) {
        myArtifactNames.add(artifact.getName());
      }
    }

    @Override
    public Collection<String> getNodes() {
      return myArtifactNames;
    }

    @Override
    public Iterator<String> getIn(String name) {
      final Set<String> included = new LinkedHashSet<String>();
      final PackagingElementResolvingContext context = myArtifactManager.getResolvingContext();
      final Artifact artifact = context.getArtifactModel().findArtifact(name);
      if (artifact != null) {
        ArtifactUtil.processPackagingElements(artifact, ArtifactElementType.ARTIFACT_ELEMENT_TYPE, new PackagingElementProcessor<ArtifactPackagingElement>() {
          @Override
          public boolean process(@NotNull ArtifactPackagingElement element,
                                 @NotNull PackagingElementPath path) {
            final String artifactName = element.getArtifactName();
            if (myArtifactNames.contains(artifactName)) {
              included.add(artifactName);
            }
            return true;
          }
        }, context, false);
      }
      return included.iterator();
    }
  }

}
