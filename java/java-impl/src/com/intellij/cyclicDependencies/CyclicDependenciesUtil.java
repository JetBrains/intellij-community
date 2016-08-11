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
package com.intellij.cyclicDependencies;

import com.intellij.util.Chunk;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;

import java.util.*;

/**
 * User: anna
 * Date: Feb 10, 2005
 */
public class CyclicDependenciesUtil{
  public static <Node> List<Chunk<Node>> buildChunks(Graph<Node> graph) {
    final DFSTBuilder<Node> dfstBuilder = new DFSTBuilder<>(graph);
    final TIntArrayList sccs = dfstBuilder.getSCCs();
    final List<Chunk<Node>> chunks = new ArrayList<>();
    sccs.forEach(new TIntProcedure() {
      int myTNumber;
      public boolean execute(int size) {
        Set<Node> packs = new LinkedHashSet<>();
        for (int j = 0; j < size; j++) {
          packs.add(dfstBuilder.getNodeByTNumber(myTNumber + j));
        }
        chunks.add(new Chunk<>(packs));
        myTNumber += size;
        return true;
      }
    });

    return chunks;
  }


  public static class Path <Node> {
    private ArrayList<Node> myPath = new ArrayList<>();

    public Path() {
    }

    public Path(Path<Node> path) {
      myPath = new ArrayList<>(path.myPath);
    }

    public Node getBeg() {
      return myPath.get(0);
    }

    public Node getEnd() {
      return myPath.get(myPath.size() - 1);
    }

    public boolean contains(Node node) {
      return myPath.contains(node);
    }

    public List<Node> getNextNodes(Node node) {
      List<Node> result = new ArrayList<>();
      for (int i = 0; i < myPath.size() - 1; i++) {
        Node nodeN = myPath.get(i);
        if (nodeN == node) {
          result.add(myPath.get(i + 1));
        }
      }
      return result;
    }

    public void add(Node node) {
      myPath.add(node);
    }

    public ArrayList<Node> getPath() {
      return myPath;
    }
  }

  public static class GraphTraverser<Node> {
    private final List<Path<Node>> myCurrentPaths = new ArrayList<>();
    private final Node myBegin;
    private final Chunk<Node> myChunk;
    private final int myMaxPathsCount;
    private final Graph<Node> myGraph;

    public GraphTraverser(final Node begin, final Chunk<Node> chunk, final int maxPathsCount, final Graph<Node> graph) {
      myBegin = begin;
      myChunk = chunk;
      myMaxPathsCount = maxPathsCount;
      myGraph = graph;
    }

    public Set<Path<Node>> traverse() {
      Set<Path<Node>> result = new HashSet<>();
      Path<Node> firstPath = new Path<>();
      firstPath.add(myBegin);
      myCurrentPaths.add(firstPath);
      while (!myCurrentPaths.isEmpty() && result.size() < myMaxPathsCount) {
        final Path<Node> path = myCurrentPaths.get(0);
        final Set<Node> nextNodes = getNextNodes(path.getEnd());
        nextStep(nextNodes, path, result);
      }
      return result;
    }

    public Set<ArrayList<Node>> convert(Set<Path<Node>> paths) {
      Set<ArrayList<Node>> result = new HashSet<>();
      for (Path<Node> path : paths) {
        result.add(path.getPath());
      }
      return result;
    }

    private void nextStep(final Set<Node> nextNodes, final Path<Node> path, Set<Path<Node>> result) {
      myCurrentPaths.remove(path);
      for (Node node : nextNodes) {
        if (path.getEnd() == node) {
          continue;
        }
        if (path.getBeg() == node) {
          result.add(path);
          continue;
        }
        Path<Node> newPath = new Path<>(path);
        newPath.add(node);
        if (path.contains(node)) {
          final Set<Node> nodesAfterInnerCycle = getNextNodes(node);
          nodesAfterInnerCycle.removeAll(path.getNextNodes(node));
          nextStep(nodesAfterInnerCycle, newPath, result);
        }
        else {
          myCurrentPaths.add(newPath);
        }
      }
    }

    private Set<Node> getNextNodes(Node node) {
      Set<Node> result = new HashSet<>();
      final Iterator<Node> in = myGraph.getIn(node);
      for (; in.hasNext();) {
        final Node inNode = in.next();
        if (myChunk.containsNode(inNode)) {
          result.add(inNode);
        }
      }
      return result;
    }
  }
}
