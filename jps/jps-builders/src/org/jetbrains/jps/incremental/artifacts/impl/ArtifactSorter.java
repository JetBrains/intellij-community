/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.artifacts.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.graph.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.artifacts.JpsBuilderArtifactService;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsArtifactOutputPackagingElement;

import java.util.*;

/**
 * @author nik
 */
public class ArtifactSorter {
  private final JpsModel myModel;
  private Map<JpsArtifact, JpsArtifact> myArtifactToSelfIncludingName;
  private List<JpsArtifact> mySortedArtifacts;

  public ArtifactSorter(JpsModel model) {
    myModel = model;
  }

  public Map<JpsArtifact, JpsArtifact> getArtifactToSelfIncludingNameMap() {
    if (myArtifactToSelfIncludingName == null) {
      myArtifactToSelfIncludingName = computeArtifactToSelfIncludingNameMap();
    }
    return myArtifactToSelfIncludingName;
  }

  public List<JpsArtifact> getArtifactsSortedByInclusion() {
    if (mySortedArtifacts == null) {
      mySortedArtifacts = doGetSortedArtifacts();
    }
    return mySortedArtifacts;
  }

  private List<JpsArtifact> doGetSortedArtifacts() {
    Graph<JpsArtifact> graph = createArtifactsGraph();
    DFSTBuilder<JpsArtifact> builder = new DFSTBuilder<>(graph);
    List<JpsArtifact> names = new ArrayList<>();
    names.addAll(graph.getNodes());
    names.sort(builder.comparator());
    return names;
  }

  private Map<JpsArtifact, JpsArtifact> computeArtifactToSelfIncludingNameMap() {
    final Map<JpsArtifact, JpsArtifact> result = new HashMap<>();
    final Graph<JpsArtifact> graph = createArtifactsGraph();
    for (JpsArtifact artifact : graph.getNodes()) {
      final Iterator<JpsArtifact> in = graph.getIn(artifact);
      while (in.hasNext()) {
        JpsArtifact next = in.next();
        if (next.equals(artifact)) {
          result.put(artifact, artifact);
          break;
        }
      }
    }

    final DFSTBuilder<JpsArtifact> builder = new DFSTBuilder<>(graph);
    if (builder.isAcyclic() && result.isEmpty()) return Collections.emptyMap();

    for (Collection<JpsArtifact> component : builder.getComponents()) {
      if (component.size() > 1) {
        for (JpsArtifact artifact : component) {
          result.put(artifact, artifact);
        }
      }
    }

    for (int i = 0; i < graph.getNodes().size(); i++) {
      final JpsArtifact artifact = builder.getNodeByTNumber(i);
      if (!result.containsKey(artifact)) {
        final Iterator<JpsArtifact> in = graph.getIn(artifact);
        while (in.hasNext()) {
          final JpsArtifact next = result.get(in.next());
          if (next != null) {
            result.put(artifact, next);
          }
        }
      }
    }

    return result;
  }

  @NotNull
  public static Set<JpsArtifact> addIncludedArtifacts(@NotNull Collection<JpsArtifact> artifacts) {
    Set<JpsArtifact> result = new HashSet<>();
    for (JpsArtifact artifact : artifacts) {
      collectIncludedArtifacts(artifact, new HashSet<>(), result, true);
    }
    return result;
  }

  private static void collectIncludedArtifacts(JpsArtifact artifact,
                                               final Set<JpsArtifact> processed,
                                               final Set<JpsArtifact> result,
                                               final boolean withOutputPathOnly) {
    if (!processed.add(artifact)) {
      return;
    }
    if (!withOutputPathOnly || !StringUtil.isEmpty(artifact.getOutputPath())) {
      result.add(artifact);
    }

    processIncludedArtifacts(artifact, included -> collectIncludedArtifacts(included, processed, result, withOutputPathOnly));
  }

  private Graph<JpsArtifact> createArtifactsGraph() {
    return GraphGenerator.generate(CachingSemiGraph.cache(new ArtifactsGraph(myModel)));
  }

  private static void processIncludedArtifacts(JpsArtifact artifact, final Consumer<JpsArtifact> consumer) {
    JpsArtifactUtil.processPackagingElements(artifact.getRootElement(), element -> {
      if (element instanceof JpsArtifactOutputPackagingElement) {
        JpsArtifact included = ((JpsArtifactOutputPackagingElement)element).getArtifactReference().resolve();
        if (included != null) {
          consumer.consume(included);
        }
        return false;
      }
      return true;
    });
  }

  private static class ArtifactsGraph implements InboundSemiGraph<JpsArtifact> {
    private final Set<JpsArtifact> myArtifactNodes;

    public ArtifactsGraph(final JpsModel model) {
      myArtifactNodes = new LinkedHashSet<>(JpsBuilderArtifactService.getInstance().getArtifacts(model, true));
    }

    @Override
    public Collection<JpsArtifact> getNodes() {
      return myArtifactNodes;
    }

    @Override
    public Iterator<JpsArtifact> getIn(JpsArtifact artifact) {
      final Set<JpsArtifact> included = new LinkedHashSet<>();
      processIncludedArtifacts(artifact, includedArtifact -> {
        if (myArtifactNodes.contains(includedArtifact)) {
          included.add(includedArtifact);
        }
      });
      return included.iterator();
    }
  }
}