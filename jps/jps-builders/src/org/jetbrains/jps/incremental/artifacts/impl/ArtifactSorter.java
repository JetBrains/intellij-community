/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.util.Processor;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.artifacts.JpsBuilderArtifactService;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsArtifactOutputPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;

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
    GraphGenerator<JpsArtifact> graph = createArtifactsGraph();
    DFSTBuilder<JpsArtifact> builder = new DFSTBuilder<JpsArtifact>(graph);
    List<JpsArtifact> names = new ArrayList<JpsArtifact>();
    names.addAll(graph.getNodes());
    Collections.sort(names, builder.comparator());
    return names;
  }

  private Map<JpsArtifact, JpsArtifact> computeArtifactToSelfIncludingNameMap() {
    final Map<JpsArtifact, JpsArtifact> result = new HashMap<JpsArtifact, JpsArtifact>();
    final GraphGenerator<JpsArtifact> graph = createArtifactsGraph();
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

    final DFSTBuilder<JpsArtifact> builder = new DFSTBuilder<JpsArtifact>(graph);
    if (builder.isAcyclic() && result.isEmpty()) return Collections.emptyMap();

    final TIntArrayList sccs = builder.getSCCs();
    sccs.forEach(new TIntProcedure() {
      int myTNumber = 0;
      public boolean execute(int size) {
        if (size > 1) {
          for (int j = 0; j < size; j++) {
            final JpsArtifact artifact = builder.getNodeByTNumber(myTNumber + j);
            result.put(artifact, artifact);
          }
        }
        myTNumber += size;
        return true;
      }
    });

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
    Set<JpsArtifact> result = new HashSet<JpsArtifact>();
    for (JpsArtifact artifact : artifacts) {
      collectIncludedArtifacts(artifact, new HashSet<JpsArtifact>(), result, true);
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

    processIncludedArtifacts(artifact, new Consumer<JpsArtifact>() {
      @Override
      public void consume(JpsArtifact included) {
        collectIncludedArtifacts(included, processed, result, withOutputPathOnly);
      }
    });
  }

  private GraphGenerator<JpsArtifact> createArtifactsGraph() {
    return GraphGenerator.create(CachingSemiGraph.create(new ArtifactsGraph(myModel)));
  }

  private static void processIncludedArtifacts(JpsArtifact artifact, final Consumer<JpsArtifact> consumer) {
    JpsArtifactUtil.processPackagingElements(artifact.getRootElement(), new Processor<JpsPackagingElement>() {
      @Override
      public boolean process(JpsPackagingElement element) {
        if (element instanceof JpsArtifactOutputPackagingElement) {
          JpsArtifact included = ((JpsArtifactOutputPackagingElement)element).getArtifactReference().resolve();
          if (included != null) {
            consumer.consume(included);
          }
          return false;
        }
        return true;
      }
    });
  }

  private static class ArtifactsGraph implements GraphGenerator.SemiGraph<JpsArtifact> {
    private final Set<JpsArtifact> myArtifactNodes;

    public ArtifactsGraph(final JpsModel model) {
      myArtifactNodes = new LinkedHashSet<JpsArtifact>(JpsBuilderArtifactService.getInstance().getArtifacts(model, true));
    }

    @Override
    public Collection<JpsArtifact> getNodes() {
      return myArtifactNodes;
    }

    @Override
    public Iterator<JpsArtifact> getIn(JpsArtifact artifact) {
      final Set<JpsArtifact> included = new LinkedHashSet<JpsArtifact>();
      final Consumer<JpsArtifact> consumer = new Consumer<JpsArtifact>() {
        @Override
        public void consume(JpsArtifact artifact) {
          if (myArtifactNodes.contains(artifact)) {
            included.add(artifact);
          }
        }
      };
      processIncludedArtifacts(artifact, consumer);
      return included.iterator();
    }
  }

}
