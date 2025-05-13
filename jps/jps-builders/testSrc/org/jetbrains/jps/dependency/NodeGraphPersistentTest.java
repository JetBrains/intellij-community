// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.impl.Containers;
import org.jetbrains.jps.dependency.impl.DependencyGraphImpl;
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder;
import org.jetbrains.jps.dependency.impl.PathSource;
import org.jetbrains.jps.dependency.java.JVMFlags;
import org.jetbrains.jps.dependency.java.JvmClass;
import org.jetbrains.jps.dependency.serializer.JvmClassTestUtil;
import org.jetbrains.jps.incremental.storage.graph.PersistentMapletFactory;
import org.jetbrains.jps.javac.Iterators;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class NodeGraphPersistentTest extends BasePlatformTestCase {
  public void testPersistentNodeGraph() throws IOException {
    // Create and fill out the graph
    File tempDirectory = FileUtil.createTempDirectory("persistent", "map");
    try (DependencyGraphImpl graph = new DependencyGraphImpl(new PersistentMapletFactory(tempDirectory.getAbsolutePath()))) {
      NodeSource aSrc = createNodeSource("A");
      NodeSource bSrc = createNodeSource("B");

      // This should be executed before compiler run
      Delta delta = graph.createDelta(Arrays.asList(aSrc, bSrc), null, false);
      JvmClass jvmClassNode = JvmClassTestUtil.createJvmClassNode();

      // Analyze after compiler
      delta.associate(jvmClassNode, Arrays.asList(aSrc, bSrc));

      // After each round, not after each builder
      DifferentiateResult differentiateResult = graph.differentiate(delta, DifferentiateParametersBuilder.withDefaultSettings());
      graph.integrate(differentiateResult);

      // Check graph
      List<NodeSource> nodeSourcesFromGraph = Iterators.collect(graph.getSources(), new ArrayList<>());
      assertContainsElements(nodeSourcesFromGraph, aSrc, bSrc);

      JvmClass jvmClassFromGraph = graph.getNodes(nodeSourcesFromGraph.get(0), JvmClass.class).iterator().next();
      JvmClass jvmClassFromGraphByDifferentSource = graph.getNodes(nodeSourcesFromGraph.get(1), JvmClass.class).iterator().next();
      JvmClassTestUtil.checkJvmClassEquals(jvmClassFromGraph, jvmClassFromGraphByDifferentSource);

      JvmClassTestUtil.checkJvmClassEquals(jvmClassNode, jvmClassFromGraph);
    }
    finally {
      FileUtil.delete(tempDirectory);
    }
  }

  public void testIntegrateNodesWithSameID() throws IOException {
    // Create and fill out the graph
    File tempDirectory = FileUtil.createTempDirectory("persistent", "map");
    try (DependencyGraphImpl graph = new DependencyGraphImpl(new PersistentMapletFactory(tempDirectory.getAbsolutePath()))) {
      NodeSource aSrc = createNodeSource("A");
      NodeSource bSrc = createNodeSource("B");

      Delta initialDelta = graph.createDelta(Arrays.asList(aSrc, bSrc), null, false);
      JvmClass clsNodeA = new JvmClass(JVMFlags.EMPTY, "", "com.ppp.aClass", "out/modA/cls", "", "", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null, Collections.emptyList(), Collections.emptyList());
      initialDelta.associate(clsNodeA, List.of(aSrc));
      JvmClass clsNodeB = new JvmClass(JVMFlags.EMPTY, "", "com.ppp.aClass", "out/modB/cls", "", "", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null, Collections.emptyList(), Collections.emptyList());
      initialDelta.associate(clsNodeB, List.of(bSrc));

      DifferentiateResult differentiateResult = graph.differentiate(initialDelta, DifferentiateParametersBuilder.create().calculateAffected(false).get());
      graph.integrate(differentiateResult);

      // Check graph
      Set<NodeSource> sourcesFromGraph = Iterators.collect(graph.getSources(), new HashSet<>());
      assertContainsElements(sourcesFromGraph, aSrc, bSrc);

      Set<Node<?, ?>> nodesFromGraph = Iterators.collect(Iterators.flat(Iterators.map(graph.getSources(), s1 -> graph.getNodes(s1))), Containers.createCustomPolicySet(DiffCapable::isSame, DiffCapable::diffHashCode));
      assertEquals(2, nodesFromGraph.size());
      assertTrue(nodesFromGraph.contains(clsNodeA));
      assertTrue(nodesFromGraph.contains(clsNodeB));

      // nodes have the same referenceID, so the sources set mapped to this ID should be the same
      Set<NodeSource> associatedSources = Iterators.collect(graph.getSources(clsNodeA.getReferenceID()), new HashSet<>());
      assertTrue(associatedSources.equals(Iterators.collect(graph.getSources(clsNodeB.getReferenceID()), new HashSet<>())));
      assertEquals(new HashSet<>(List.of(aSrc, bSrc)), associatedSources);

      // emulate contents of aSrc changed and now a node with different changeID is associated with this source
      Delta delta = graph.createDelta(Arrays.asList(aSrc), null, false);
      JvmClass clsNodeAChanged = new JvmClass(JVMFlags.EMPTY, "", "com.ppp.aChangedClass", "out/modA/cls", "", "", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null, Collections.emptyList(), Collections.emptyList());
      delta.associate(clsNodeAChanged, List.of(aSrc));
      DifferentiateResult changes = graph.differentiate(delta, DifferentiateParametersBuilder.create().calculateAffected(false).get());
      graph.integrate(changes);

      assertEquals(new HashSet<>(List.of(aSrc)), Iterators.collect(graph.getSources(clsNodeAChanged.getReferenceID()), new HashSet<>()));
      assertEquals(new HashSet<>(List.of(bSrc)), Iterators.collect(graph.getSources(clsNodeB.getReferenceID()), new HashSet<>()));
      assertEquals(new HashSet<>(List.of(bSrc)), Iterators.collect(graph.getSources(clsNodeA.getReferenceID()), new HashSet<>()));

      Set<Node<?, ?>> nodesFromGraphAfterChange = Iterators.collect(Iterators.flat(Iterators.map(graph.getSources(), s -> graph.getNodes(s))), Containers.createCustomPolicySet(DiffCapable::isSame, DiffCapable::diffHashCode));
      assertEquals(2, nodesFromGraphAfterChange.size());
      assertTrue(nodesFromGraphAfterChange.contains(clsNodeAChanged));
      assertTrue(nodesFromGraphAfterChange.contains(clsNodeB));
    }
    finally {
      FileUtil.delete(tempDirectory);
    }
  }

  private static NodeSource createNodeSource(String fileName) {
    return new PathSource("src/" + fileName + ".java");
  }
}
