// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.BackDependencyIndex;
import org.jetbrains.jps.dependency.Delta;
import org.jetbrains.jps.dependency.DependencyGraph;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.impl.DependencyGraphImpl;
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder;
import org.jetbrains.jps.dependency.impl.MemoryMapletFactory;
import org.jetbrains.jps.dependency.impl.PathSource;
import org.jetbrains.jps.dependency.java.JVMFlags;
import org.jetbrains.jps.dependency.java.JvmClass;
import org.jetbrains.jps.dependency.java.JvmField;
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID;
import org.jetbrains.jps.dependency.java.SubclassesIndex;
import org.jetbrains.jps.dependency.java.Utils;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.jetbrains.jps.util.Iterators.collect;
import static org.jetbrains.jps.util.Iterators.flat;
import static org.jetbrains.jps.util.Iterators.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DependencyGraphTest {

  @Test
  public void testShsdowedNodesIndexConsistency() throws IOException {
    try (DependencyGraph graph = new DependencyGraphImpl(new MemoryMapletFactory())) {
      NodeSource srcA1 = createSource("module-a/src-1");
      NodeSource srcA2 = createSource("module-a/src-2");
      NodeSource srcB1 = createSource("module-b/src-1");
      NodeSource srcB2 = createSource("module-b/src-2");

      JvmClass dogA = createClass("a", "Dog", "Animal", List.of(), List.of(createField("name", "Ljava/lang/String;")));
      JvmClass catB = createClass("b", "Cat", "Animal", List.of(), List.of());
      JvmClass dogB = createClass("b", "Dog", "Animal", List.of(), List.of(createField("race", "I"))); // another version of Dog with the same FQ name as the one in module A

      Delta delta = graph.createDelta(List.of(srcA1, srcA2, srcB1, srcB2), List.of(), false);
      delta.associate(dogA, List.of(srcA1, srcA2));
      delta.associate(catB, List.of(srcB1));
      delta.associate(dogB, List.of(srcB2));
      graph.integrate(
        graph.differentiate(delta, DifferentiateParametersBuilder.withDefaultSettings())
      );

      assertEquals(
        Set.of(srcA1, srcA2, srcB1, srcB2),
        collect(graph.getSources(), new HashSet<>())
      );
      assertEquals(
        Set.of(dogA, dogB, catB),
        collect(flat(map(graph.getSources(), graph::getNodes)), new HashSet<>())
      );
      assertEquals(
        Set.of("Cat", "Dog"),
        collect(getAllDirectSubclasses(graph, "Animal"), new HashSet<>())
      );

      // create delta for deleted sources and apply changes to graph => dogA class node gets deleted
      graph.integrate(
        graph.differentiate(graph.createDelta(List.of(), List.of(srcA1, srcA2), true), DifferentiateParametersBuilder.withDefaultSettings())
      );

      assertEquals(
        Set.of(srcB1, srcB2),
        collect(graph.getSources(), new HashSet<>())
      );
      assertEquals(
        Set.of(dogB, catB),
        collect(flat(map(graph.getSources(), graph::getNodes)), new HashSet<>())
      );
      assertEquals(
        Set.of("Cat", "Dog"),
        collect(getAllDirectSubclasses(graph, "Animal"), new HashSet<>())
      );
    }
  }

  @Test
  public void testIsSameOrInheritorOfTransitiveHierarchy() throws IOException {
    try (DependencyGraph graph = new DependencyGraphImpl(new MemoryMapletFactory())) {
      NodeSource src = createSource("module-a/src");

      JvmField protectedField = createField(Opcodes.ACC_PROTECTED, "state", "I");
      JvmClass grand = createClass("a", "pkgA/A", "java/lang/Object", List.of(), List.of(protectedField));
      JvmClass parent = createClass("a", "pkgB/B", "pkgA/A", List.of(), List.of());
      JvmClass child = createClass("a", "pkgC/C", "pkgB/B", List.of(), List.of());

      Delta delta = graph.createDelta(List.of(src), List.of(), false);
      delta.associate(grand, List.of(src));
      delta.associate(parent, List.of(src));
      delta.associate(child, List.of(src));
      graph.integrate(
        graph.differentiate(delta, DifferentiateParametersBuilder.withDefaultSettings())
      );

      Utils utils = new Utils(graph, __ -> true);

      assertTrue(utils.isSameOrInheritorOf(child, child));
      assertTrue(utils.isSameOrInheritorOf(child, parent));
      // the walk must reach classes beyond the direct supertype: C extends B extends A
      assertTrue(utils.isSameOrInheritorOf(child, grand));
      assertFalse(utils.isSameOrInheritorOf(grand, child));

      // a protected member of A is visible in the transitive subclass C residing in another package
      assertTrue(utils.isVisibleIn(grand, protectedField, child));
    }
  }

  private static @NotNull Iterable<String> getAllDirectSubclasses(DependencyGraph graph, String classFqName) {
    BackDependencyIndex subclassIndex = graph.getIndex(SubclassesIndex.NAME);
    return map(
      subclassIndex.getDependencies(new JvmNodeReferenceID(classFqName)),
      id -> id instanceof JvmNodeReferenceID _id? _id.getNodeName() : id.toString()
    );
  }

  private static JvmClass createClass(String moduleName, String fqName) {
    return createClass(moduleName, fqName, "java/lang/Object", List.of(), List.of());
  }
  
  private static JvmClass createClass(String moduleName, String fqName, String parentFqName, Iterable<String> interfaces, Iterable<JvmField> fields) {
    return new JvmClass(
      new JVMFlags(Opcodes.ACC_PUBLIC), "", fqName, moduleName + ":" + fqName.replace('.', '/') + ".class", parentFqName, "",
      interfaces, // interfaces
      fields, // fields
      List.of(), // methods
      List.of(), // annotations
      List.of(), // type annotations
      List.of(), // annotation targets
      null, // retention policy
      List.of(), // usages
      List.of()  // metadata
    );
  }

  private static JvmField createField(String name, String descriptor) {
    return createField(Opcodes.ACC_PUBLIC, name, descriptor);
  }

  private static JvmField createField(int access, String name, String descriptor) {
    return new JvmField(new JVMFlags(access), "", name, descriptor, List.of(), List.of(), null);
  }

  private static NodeSource createSource(String path) {
    return new PathSource(path);
  }
}
