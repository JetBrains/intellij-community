/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.packaging;

import com.intellij.compiler.impl.packagingCompiler.JarInfo;
import com.intellij.compiler.impl.packagingCompiler.JarsBuilder;
import com.intellij.compiler.impl.packagingCompiler.PackagingProcessingItem;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.Pair;
import gnu.trove.THashSet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
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
    final PackagingProcessingItem[] processingItems = buildItems(info.myBuildRecipe, configuration);

    List<Pair<JarInfo,String>> list = getJarsContent(processingItems);
    final Set<JarInfo> linkedSet = new LinkedHashSet<JarInfo>();
    for (Pair<JarInfo, String> pair : list) {
      linkedSet.add(pair.getFirst());
    }

    final MyJarsBuilder builder = new MyJarsBuilder(linkedSet);
    builder.buildJars(new HashSet<String>());
    assertTrue(builder.myCreatedTempFiles.toString(), builder.myCreatedTempFiles.isEmpty());
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
    private Map<String, String> mySources = new HashMap<String, String>();
    private Set<String> myCreatedTempFiles = new HashSet<String>();
    private String myCurrentJar;

    public MyJarsBuilder(final Set<JarInfo> jarsToBuild) {
      super(jarsToBuild, null, new MyDummyCompileContext());
    }

    protected void renameFile(final File fromFile, final File toFile, final Set<String> writtenPaths) throws IOException {
      copyFile(fromFile, toFile, writtenPaths);
      mySources.put(toFile.getPath(), fromFile.getPath());
      deleteFile(fromFile);
    }

    protected void copyFile(final File fromFile, final File toFile, final Set<String> writtenPaths) throws IOException {
      String fromPath = fromFile.getPath();
      if (mySources.containsKey(fromPath)) {
        fromPath = mySources.get(fromPath);
      }
      myOutput.add(fromPath + " -> " + toFile.getPath());
    }

    protected JarOutputStream createJarOutputStream(final File jarFile, final Manifest manifest) throws IOException {
      myCurrentJar = jarFile.getPath();
      final JarOutputStream outputStream = new JarOutputStream(new ByteArrayOutputStream());
      outputStream.putNextEntry(new ZipEntry("dummy"));
      return outputStream;
    }

    protected void deleteFile(final File file) {
      myCreatedTempFiles.remove(FileUtil.toSystemIndependentName(file.getPath()));
    }

    protected File createTempFile() throws IOException {
      String path = "/temp/file" + myTempFileCount++ + ".jar";
      myCreatedTempFiles.add(path);
      return new File(path);
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
