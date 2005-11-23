/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler;

import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.ArrayUtil;
import junit.framework.TestCase;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 27, 2004
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class ChunkGraphTest extends TestCase{

  public void testGraph1() {
    final Map<String, String[]> arcs = new HashMap<String, String[]>();
    arcs.put("a", ArrayUtil.EMPTY_STRING_ARRAY);
    arcs.put("b", new String[] {"a", "c"});
    arcs.put("c", new String[] {"b"});
    arcs.put("d", new String[] {"c", "e"});
    arcs.put("e", new String[] {"d"});

    final Graph<Chunk<String>> graph = ModuleCompilerUtil.toChunkGraph(setupGraph(new String[] {"a", "b", "c", "d", "e"}, arcs));

    final List<Chunk<String>> expectedNodes = new ArrayList<Chunk<String>>();
    Chunk<String> A = new Chunk<String>("a");
    expectedNodes.add(A);
    Chunk<String> BC = new Chunk<String>(toSet(new String[]{"b", "c"}));
    expectedNodes.add(BC);
    Chunk<String> DE = new Chunk<String>(toSet(new String[]{"d", "e"}));
    expectedNodes.add(DE);

    checkVertices(expectedNodes, graph.getNodes().iterator());

    final Map<Chunk<String>, Set<Chunk<String>>> expectedArcs = new HashMap<Chunk<String>, Set<Chunk<String>>>();
    expectedArcs.put(A, toSet());
    expectedArcs.put(BC, toSet(A));
    expectedArcs.put(DE, toSet(BC));

    checkArcs(expectedArcs, graph);
  }

  public void testGraph2() {
    final Map<String, String[]> arcs = new HashMap<String, String[]>();
    arcs.put("a", new String[] {"b", "c"});
    arcs.put("b", new String[] {"a"});
    arcs.put("c", new String[] {"b"});
    arcs.put("d", new String[] {"c"});

    final Graph<Chunk<String>> graph = ModuleCompilerUtil.toChunkGraph(setupGraph(new String[] {"a", "b", "c", "d"}, arcs));

    final List<Chunk<String>> expectedNodes = new ArrayList<Chunk<String>>();
    Chunk<String> ABC = new Chunk<String>(toSet(new String[]{"a", "b", "c"}));
    expectedNodes.add(ABC);
    Chunk<String> D = new Chunk<String>("d");
    expectedNodes.add(D);

    checkVertices(expectedNodes, graph.getNodes().iterator());

    final Map<Chunk<String>, Set<Chunk<String>>> expectedArcs = new HashMap<Chunk<String>, Set<Chunk<String>>>();
    expectedArcs.put(ABC, toSet());
    expectedArcs.put(D, toSet(ABC));

    checkArcs(expectedArcs, graph);
  }

  private void checkArcs(Map<Chunk<String>, Set<Chunk<String>>> expectedArcs, Graph<Chunk<String>> graph) {
    final Iterator<Chunk<String>> nodes = graph.getNodes().iterator();
    while(nodes.hasNext()) {
      final Chunk<String> chunk = nodes.next();
      final List<Chunk<String>> ins = new ArrayList<Chunk<String>>();
      final Iterator<Chunk<String>> insIterator = graph.getIn(chunk);
      while (insIterator.hasNext()) {
        ins.add(insIterator.next());
      }
      final Set<Chunk<String>> expectedIns = expectedArcs.get(chunk);
      assertTrue(expectedIns.size() == ins.size());
      assertTrue(expectedIns.equals(new HashSet<Chunk<String>>(ins)));
    }
  }

  private <T> Set<T> toSet(T[] strings) {
    return new HashSet<T>(Arrays.asList(strings));
  }

  private Set<Chunk<String>> toSet() {
    return new HashSet<Chunk<String>>();
  }

  private Set<Chunk<String>> toSet(Chunk<String> c) {
    Set<Chunk<String>> set = toSet();
    set.add(c);
    return set;
  }

  private Set<Chunk<String>> toSet(Chunk<String> c, Chunk<String> c1) {
    Set<Chunk<String>> set = toSet(c);
    set.add(c1);
    return set;
  }

  private Set<Chunk<String>> toSet(Chunk<String> c, Chunk<String> c1, Chunk<String> c2) {
    Set<Chunk<String>> set = toSet(c, c1);
    set.add(c2);
    return set;
  }


  private void checkVertices(List<Chunk<String>> expected, Iterator<Chunk<String>> nodes) {
    List<Chunk<String>> realNodes = new ArrayList<Chunk<String>>();
    while (nodes.hasNext()) {
      realNodes.add(nodes.next());
    }
    assertTrue(expected.size() == realNodes.size());
    assertTrue(new HashSet<Chunk<String>>(expected).equals(new HashSet<Chunk<String>>(realNodes)));
  }

  private static Graph<String> setupGraph(final String[] names, final Map<String, String[]> ins) {
    return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<String>() {
      public Collection<String> getNodes() {
        return new ArrayList<String>(Arrays.asList(names));
      }

      public Iterator<String> getIn(String name) {
        return Arrays.asList(ins.get(name)).iterator();
      }
    }));
  }

}
