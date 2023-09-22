// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public final class ArtifactSorter {
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
    List<JpsArtifact> names = new ArrayList<>(graph.getNodes());
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

  public static @NotNull Set<JpsArtifact> addIncludedArtifacts(@NotNull Collection<? extends JpsArtifact> artifacts) {
    Set<JpsArtifact> result = new HashSet<>();
    for (JpsArtifact artifact : artifacts) {
      collectIncludedArtifacts(artifact, new HashSet<>(), result, true);
    }
    return result;
  }

  private static void collectIncludedArtifacts(JpsArtifact artifact,
                                               final Set<? super JpsArtifact> processed,
                                               final Set<? super JpsArtifact> result,
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

  private static void processIncludedArtifacts(JpsArtifact artifact, final Consumer<? super JpsArtifact> consumer) {
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

  private static final class ArtifactsGraph implements InboundSemiGraph<JpsArtifact> {
    private final Set<JpsArtifact> myArtifactNodes;

    ArtifactsGraph(final JpsModel model) {
      myArtifactNodes = new LinkedHashSet<>(JpsBuilderArtifactService.getInstance().getArtifacts(model, true));
    }

    @Override
    public @NotNull Collection<JpsArtifact> getNodes() {
      return myArtifactNodes;
    }

    @Override
    public @NotNull Iterator<JpsArtifact> getIn(JpsArtifact artifact) {
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