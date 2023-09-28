// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.jps.dependency.impl.DependencyGraphImpl;
import org.jetbrains.jps.dependency.impl.FileSource;
import org.jetbrains.jps.dependency.java.JvmClass;
import org.jetbrains.jps.dependency.serializer.JvmClassTestUtil;

import java.io.*;
import java.util.Arrays;
import java.util.Iterator;

public class NodeGraphPersistentTest extends BasePlatformTestCase {
  public void testPersistentNodeGraph() {
    // Create and fill out the graph
    DependencyGraphImpl graph = new DependencyGraphImpl();
    FileSource aNode = createFileSourceNode("A");
    FileSource bNode = createFileSourceNode("B");
    Delta delta = graph.createDelta(Arrays.asList(aNode, bNode), null);
    JvmClass jvmClassNode = JvmClassTestUtil.createJvmClassNode();
    delta.associate(jvmClassNode, Arrays.asList(aNode, bNode));

    DifferentiateResult differentiateResult = graph.differentiate(delta);
    graph.integrate(differentiateResult);

    // Check graph
    Iterator<NodeSource> nodeSourcesFromGraph = graph.getSources().iterator();
    assertEquals(aNode, nodeSourcesFromGraph.next());
    assertEquals(bNode, nodeSourcesFromGraph.next());

    JvmClass jvmClassFromGraph = (JvmClass)graph.getNodes(graph.getSources().iterator().next()).iterator().next();
    JvmClassTestUtil.checkJvmClassEquals(jvmClassNode, jvmClassFromGraph);
  }

  private static FileSource createFileSourceNode(String fileName) {
    return new FileSource(new File("src/" + fileName + ".java"));
  }
}
