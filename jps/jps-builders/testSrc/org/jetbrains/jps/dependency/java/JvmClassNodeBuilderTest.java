// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import org.jetbrains.jps.javac.Iterators;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
}
