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
package org.jetbrains.jps.incremental.artifacts.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import groovy.lang.Closure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.artifacts.Artifact;
import org.jetbrains.jps.artifacts.ArtifactLayoutElement;
import org.jetbrains.jps.artifacts.ComplexLayoutElement;
import org.jetbrains.jps.model.JpsModel;

import java.util.*;

/**
 * @author nik
 */
public class ArtifactSorter {
  private final Project myProject;
  private final JpsModel myModel;
  private Map<String, String> myArtifactToSelfIncludingName;
  private List<String> mySortedArtifacts;

  public ArtifactSorter(Project project, JpsModel model) {
    myProject = project;
    myModel = model;
  }

  public Map<String, String> getArtifactToSelfIncludingNameMap() {
    if (myArtifactToSelfIncludingName == null) {
      myArtifactToSelfIncludingName = computeArtifactToSelfIncludingNameMap();
    }
    return myArtifactToSelfIncludingName;
  }

  public List<String> getArtifactsSortedByInclusion() {
    if (mySortedArtifacts == null) {
      mySortedArtifacts = doGetSortedArtifacts();
    }
    return mySortedArtifacts;
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

  @NotNull
  public static Set<Artifact> addIncludedArtifacts(@NotNull Collection<Artifact> artifacts, @NotNull Project project, JpsModel model) {
    Set<Artifact> result = new HashSet<Artifact>();
    for (Artifact artifact : artifacts) {
      collectIncludedArtifacts(artifact, project, model, new HashSet<Artifact>(), result, true);
    }
    return result;
  }

  private static void collectIncludedArtifacts(Artifact artifact,
                                               final Project project,
                                               final JpsModel model,
                                               final Set<Artifact> processed,
                                               final Set<Artifact> result,
                                               final boolean withOutputPathOnly) {
    if (!processed.add(artifact)) {
      return;
    }
    if (!withOutputPathOnly || !StringUtil.isEmpty(artifact.getOutputPath())) {
      result.add(artifact);
    }

    processIncludedArtifacts(artifact, project, model, new Consumer<Artifact>() {
      @Override
      public void consume(Artifact included) {
        collectIncludedArtifacts(included, project, model, processed, result, withOutputPathOnly);
      }
    });
  }

  private GraphGenerator<String> createArtifactsGraph() {
    return GraphGenerator.create(CachingSemiGraph.create(new ArtifactsGraph(myProject, myModel)));
  }

  private static void processIncludedArtifacts(Artifact artifact,
                                               final Project project,
                                               JpsModel model, final Consumer<Artifact> consumer) {
    artifact.getRootElement().process(project, model, new Closure(consumer) {
      @Override
      public Object call(Object arguments) {
        if (arguments instanceof ArtifactLayoutElement) {
          final Artifact includedArtifact = ((ArtifactLayoutElement)arguments).findArtifact(project);
          if (includedArtifact != null) {
            consumer.consume(includedArtifact);
          }
        }
        if (arguments instanceof ComplexLayoutElement) {
          return false;
        }
        return true;
      }
    });
  }

  private static class ArtifactsGraph implements GraphGenerator.SemiGraph<String> {
    private final Set<String> myArtifactNames;
    private final Project myProject;
    private final JpsModel myModel;

    public ArtifactsGraph(Project project, JpsModel model) {
      myProject = project;
      myModel = model;
      myArtifactNames = new LinkedHashSet<String>(project.getArtifacts().keySet());
    }

    @Override
    public Collection<String> getNodes() {
      return myArtifactNames;
    }

    @Override
    public Iterator<String> getIn(String name) {
      final Set<String> included = new LinkedHashSet<String>();
      final Artifact artifact = myProject.getArtifacts().get(name);
      if (artifact != null) {
        final Consumer<Artifact> consumer = new Consumer<Artifact>() {
          @Override
          public void consume(Artifact artifact) {
            if (myArtifactNames.contains(artifact.getName())) {
              included.add(artifact.getName());
            }
          }
        };
        processIncludedArtifacts(artifact, myProject, myModel, consumer);
      }
      return included.iterator();
    }
  }

}
