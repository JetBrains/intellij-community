/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.packaging;

import com.intellij.compiler.impl.make.newImpl.JarInfo;
import com.intellij.compiler.impl.make.newImpl.JarsBuilder;
import com.intellij.compiler.impl.make.newImpl.NewProcessingItem;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.compiler.DummyCompileContext;
import gnu.trove.THashSet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * @author nik
 */
public class JarsBuilderTest extends IncrementalPackagingTestCase {

  public void testCopyDirToJar() throws Exception {
    doTest(false, true, false,
           start()
             .copy("dir", "/", "a.jsp", "b.jsp"));
  }

  public void testJarDir() throws Exception {
    doTest(true, true, false,
           start()
             .jar("dir", "/a.jar", "x.class"));
  }

  public void testWarAndEar() throws Exception {
    doTest(true, true, false, start()
      .copy("lib/b.jar", "/b.jar")
      .inner("w.war", true, true, false)
        .jar("dir", "/a.jar", "a.jsp")
        .up()
    );
  }

  public void testCopyExternalDepsToEar() throws Exception {
    doTest(false, true, false, start()
      .inner("w.war", false, false, false)
        .copy("a.jsp", "/a.jsp")
        .copy("b.jar", "../b.jar")
        .up()
      .copy("lib/c.jar", "/c.jar"));
  }

  public void testJarExternalDepsToEar() throws Exception {
    doTest(true, true, false, start()
      .inner("w.war", false, false, false)
        .jar("dir", "../d.jar", "b.jsp", "c.jsp")
        .up()
      .copy("lib/e.jar", "/e.jar"));
  }

  private void doTest(final boolean explodedEnabled, final boolean jarEnabled, final boolean buildExternalDependencies,
                      final BuildRecipeInfo info) throws Exception {
    final MockBuildConfiguration configuration = new MockBuildConfiguration(explodedEnabled, jarEnabled, buildExternalDependencies);
    final NewProcessingItem[] processingItems = buildItems(info.myBuildRecipe, configuration);
    final HashSet<JarInfo> hashSet = new HashSet<JarInfo>();
    fillAllJars(processingItems, hashSet);
    final MyJarsBuilder builder = new MyJarsBuilder(hashSet);
    builder.buildJars(new HashSet<String>());
    String expected = loadText(getExpectedFile(getTestName(true)));
    assertEquals(expected, builder.getOutput());
  }

  private File getExpectedFile(final String testName) {
    return new File(PathManagerEx.getTestDataPath(),  "compiler" + File.separator + "packaging" + File.separator +
                                                      "jarsBuilder" + File.separator + testName + ".txt");
  }

  private static class MyJarsBuilder extends JarsBuilder {
    private int myTempFileCount = 0;
    private List<String> myOutput = new ArrayList<String>();
    private String myCurrentJar;

    public MyJarsBuilder(final Set<JarInfo> jarsToBuild) {
      super(jarsToBuild, null, DummyCompileContext.getInstance());
    }

    protected void copyFile(final File fromFile, final File toFile, final Set<String> writtenPaths) throws IOException {
      myOutput.add(fromFile.getPath() + " -> " + toFile.getPath());
    }

    protected JarOutputStream createJarOutputStream(final File jarFile) throws IOException {
      myCurrentJar = jarFile.getPath();
      final JarOutputStream outputStream = new JarOutputStream(new ByteArrayOutputStream());
      outputStream.putNextEntry(new ZipEntry("dummy"));
      return outputStream;
    }

    protected File createTempFile() throws IOException {
      return new File("/temp/file" + myTempFileCount++ + ".jar");
    }

    protected void addFileToJar(final JarOutputStream jarOutputStream, final File file, final String relativePath,
                                final THashSet<String> writtenPaths) throws IOException {
      myOutput.add(file.getPath() + " -> " + relativePath + " in " + myCurrentJar);
    }

    private String getOutput() {
      final StringBuilder builder = new StringBuilder();
      Collections.sort(myOutput);
      for (String s : myOutput) {
        builder.append(s).append("\n");
      }
      return builder.toString();
    }
  }
}
