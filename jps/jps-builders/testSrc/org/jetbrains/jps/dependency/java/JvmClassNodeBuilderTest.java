// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import org.jetbrains.jps.util.Iterators;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class JvmClassNodeBuilderTest extends TestCase {

  private final File myWorkDir;

  public JvmClassNodeBuilderTest() {
    myWorkDir = new File(PathManagerEx.getTestDataPath(getClass()) + File.separator  + "compileServer" + File.separator + "dependency");
  }

  public void testClassParsing() throws IOException {
    File dataFile = new File(myWorkDir, "I.class");
    final ClassReader reader = new FailSafeClassReader(FileUtil.loadFileBytes(dataFile));
    JvmClassNodeBuilder nodeBuilder = JvmClassNodeBuilder.create(dataFile.getPath(), reader, false);
    JVMClassNode<?, ?> classNode = nodeBuilder.getResult();

    assertTrue(classNode instanceof JvmClass);

    JvmClass cls = (JvmClass)classNode;
    assertTrue(cls.isInterface());
    assertFalse(cls.isAnnotation());

    List<JvmMethod> methods = Iterators.collect(cls.getMethods(), new ArrayList<>());
    assertEquals(1, methods.size());
    assertEquals("bar", methods.iterator().next().getName());
  }

  public void testRecurseIterator() {
    // dependencies
    Map<String, Iterable<String>> nodes = new HashMap<>();
    nodes.put("A", List.of("B", "C", "D"));
    nodes.put("B", List.of("E", "F"));
    nodes.put("C", List.of("E", "G"));
    nodes.put("D", List.of("H"));
    nodes.put("E", List.of("I"));
    nodes.put("I", List.of("A"));

    runTraversal("Recurse including head", "A,B,C,D,E,F,I,G,H", Iterators.recurse("A", n -> Iterators.filter(nodes.get(n), Objects::nonNull), true));
    runTraversal("Recurse without head", "B,C,D,E,F,I,G,H", Iterators.recurse("A", n -> Iterators.filter(nodes.get(n), Objects::nonNull), false));

    runTraversal("RecurseDeep including head", "A,B,E,I,F,C,G,D,H", Iterators.recurseDepth("A", n -> Iterators.filter(nodes.get(n), Objects::nonNull), true));
    runTraversal("RecurseDeep without head", "B,E,I,F,C,G,D,H", Iterators.recurseDepth("A", n -> Iterators.filter(nodes.get(n), Objects::nonNull), false));
  }

  private static void runTraversal(String message, String expected, Iterable<String> sequence) {
    assertEquals("1. " + message, expected, traverse(sequence));
    assertEquals("2. " + message, expected, traverse(sequence));
    assertEquals("3. " + message, expected, traverse(sequence));
  }

  private static String traverse(Iterable<String> sequence) {
    StringBuilder buf = new StringBuilder();
    for (String s : sequence) {
      if (!buf.isEmpty()) {
        buf.append(",");
      }
      buf.append(s);
    }
    return buf.toString();
  }

}
