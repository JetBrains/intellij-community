// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.jps.dependency.impl.Containers;
import org.jetbrains.jps.dependency.impl.DependencyGraphImpl;
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder;
import org.jetbrains.jps.dependency.impl.FileSource;
import org.jetbrains.jps.dependency.java.JvmClass;
import org.jetbrains.jps.dependency.serializer.JvmClassTestUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

public class NodeGraphPersistentTest extends BasePlatformTestCase {
  public void testPersistentNodeGraph() throws IOException {
    // Create and fill out the graph
    File tempDirectory = FileUtil.createTempDirectory("persistent", "map");
    DependencyGraphImpl graph = new DependencyGraphImpl(Containers.createPersistentContainerFactory(tempDirectory.getAbsolutePath()));
    try {
      FileSource aSrc = createFileSourceNode("A");
      FileSource bSrc = createFileSourceNode("B");

      // This should be executed before compiler run
      Delta delta = graph.createDelta(Arrays.asList(aSrc, bSrc), null);
      JvmClass jvmClassNode = JvmClassTestUtil.createJvmClassNode();

      // Analyze after compiler
      delta.associate(jvmClassNode, Arrays.asList(aSrc, bSrc));

      // After each round, not after each builder
      DifferentiateResult differentiateResult = graph.differentiate(delta, DifferentiateParametersBuilder.withDefaultSettings());
      graph.integrate(differentiateResult);

      // Check graph
      Iterator<NodeSource> nodeSourcesFromGraph = graph.getSources().iterator();
      assertEquals(aSrc, nodeSourcesFromGraph.next());
      assertEquals(bSrc, nodeSourcesFromGraph.next());

      JvmClass jvmClassFromGraph = (JvmClass)graph.getNodes(graph.getSources().iterator().next()).iterator().next();
      JvmClassTestUtil.checkJvmClassEquals(jvmClassNode, jvmClassFromGraph);
    }
    finally {
      graph.close();
      FileUtil.delete(tempDirectory);
    }
  }

  private static FileSource createFileSourceNode(String fileName) {
    return new FileSource(new File("src/" + fileName + ".java"));
  }
}
