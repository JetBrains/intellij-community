/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.impl.compiler.ArtifactCompilerUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.io.ZipUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author nik
 */
public class JarsBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.packagingCompiler.JarsBuilder");
  private final Set<JarInfo> myJarsToBuild;
  private final FileFilter myFileFilter;
  private final CompileContext myContext;
  private Map<JarInfo, File> myBuiltJars;

  public JarsBuilder(Set<JarInfo> jarsToBuild, FileFilter fileFilter, CompileContext context) {
    DependentJarsEvaluator evaluator = new DependentJarsEvaluator();
    for (JarInfo jarInfo : jarsToBuild) {
      evaluator.addJarWithDependencies(jarInfo);
    }
    myJarsToBuild = evaluator.getJars();
    myFileFilter = fileFilter;
    myContext = context;
  }

  public boolean buildJars(Set<String> writtenPaths) throws IOException {
    myContext.getProgressIndicator().setText(CompilerBundle.message("packaging.compiler.message.building.archives"));

    final JarInfo[] sortedJars = sortJars();
    if (sortedJars == null) {
      return false;
    }

    myBuiltJars = new HashMap<JarInfo, File>();
    try {
      for (JarInfo jar : sortedJars) {
        myContext.getProgressIndicator().checkCanceled();
        buildJar(jar);
      }

      myContext.getProgressIndicator().setText(CompilerBundle.message("packaging.compiler.message.copying.archives"));
      copyJars(writtenPaths);
    }
    finally {
      deleteTemporaryJars();
    }


    return true;
  }

  private void deleteTemporaryJars() {
    for (File file : myBuiltJars.values()) {
      FileUtil.delete(file);
    }
  }

  private void copyJars(final Set<String> writtenPaths) throws IOException {
    for (Map.Entry<JarInfo, File> entry : myBuiltJars.entrySet()) {
      File fromFile = entry.getValue();
      boolean first = true;
      for (DestinationInfo destination : entry.getKey().getAllDestinations()) {
        if (destination instanceof ExplodedDestinationInfo) {
          File toFile = new File(FileUtil.toSystemDependentName(destination.getOutputPath()));

          if (first) {
            first = false;
            renameFile(fromFile, toFile, writtenPaths);
            fromFile = toFile;
          }
          else {
            DeploymentUtil.getInstance().copyFile(fromFile, toFile, myContext, writtenPaths, myFileFilter);
          }

        }
      }
    }
  }

  private static void renameFile(final File fromFile, final File toFile, final Set<String> writtenPaths) throws IOException {
    FileUtil.rename(fromFile, toFile);
    writtenPaths.add(toFile.getPath());
  }

  @Nullable
  private JarInfo[] sortJars() {
    final DFSTBuilder<JarInfo> builder = new DFSTBuilder<JarInfo>(GraphGenerator.create(CachingSemiGraph.create(new JarsGraph())));
    if (!builder.isAcyclic()) {
      final Pair<JarInfo, JarInfo> dependency = builder.getCircularDependency();
      String message = CompilerBundle.message("packaging.compiler.error.cannot.build.circular.dependency.found.between.0.and.1",
                                              dependency.getFirst().getPresentableDestination(),
                                              dependency.getSecond().getPresentableDestination());
      myContext.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
      return null;
    }

    JarInfo[] jars = myJarsToBuild.toArray(new JarInfo[myJarsToBuild.size()]);
    Arrays.sort(jars, builder.comparator());
    jars = ArrayUtil.reverseArray(jars);
    return jars;
  }

  public Set<JarInfo> getJarsToBuild() {
    return myJarsToBuild;
  }

  private void buildJar(final JarInfo jar) throws IOException {
    if (jar.getPackedFiles().isEmpty() && jar.getPackedJars().isEmpty()) {
      myContext.addMessage(CompilerMessageCategory.WARNING, "Archive '" + jar.getPresentableDestination() + "' has no files so it won't be created", null, -1, -1);
      return;
    }

    myContext.getProgressIndicator()
      .setText(CompilerBundle.message("packaging.compiler.message.building.0", jar.getPresentableDestination()));
    File jarFile = FileUtil.createTempFile("artifactCompiler", "tmp");
    myBuiltJars.put(jar, jarFile);

    FileUtil.createParentDirs(jarFile);
    final JarOutputStream jarOutputStream = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(jarFile)));

    try {
      final THashSet<String> writtenPaths = new THashSet<String>();
      for (Pair<String, VirtualFile> pair : jar.getPackedFiles()) {
        final VirtualFile sourceFile = pair.getSecond();
        if (sourceFile.isInLocalFileSystem()) {
          File file = VfsUtil.virtualToIoFile(sourceFile);
          addFileToJar(jarOutputStream, file, pair.getFirst(), writtenPaths);
        }
        else {
          extractFileAndAddToJar(jarOutputStream, sourceFile, pair.getFirst(), writtenPaths);
        }
      }

      for (Pair<String, JarInfo> nestedJar : jar.getPackedJars()) {
        File nestedJarFile = myBuiltJars.get(nestedJar.getSecond());
        if (nestedJarFile != null) {
          addFileToJar(jarOutputStream, nestedJarFile, nestedJar.getFirst(), writtenPaths);
        }
        else {
          LOG.debug("nested jar file " + nestedJar.getFirst() + " for " + jar.getPresentableDestination() + " not found");
        }
      }
    }
    finally {
      jarOutputStream.close();
    }
  }

  private void extractFileAndAddToJar(JarOutputStream jarOutputStream, VirtualFile sourceFile, String relativePath, THashSet<String> writtenPaths)
    throws IOException {
    relativePath = addParentDirectories(jarOutputStream, writtenPaths, relativePath);
    myContext.getProgressIndicator().setText2(relativePath);
    if (!writtenPaths.add(relativePath)) return;

    final BufferedInputStream input = ArtifactCompilerUtil.getJarEntryInputStream(sourceFile, myContext);
    if (input == null) return;

    ZipEntry entry = new ZipEntry(relativePath);
    entry.setTime(ArtifactCompilerUtil.getJarFile(sourceFile).lastModified());
    jarOutputStream.putNextEntry(entry);
    FileUtil.copy(input, jarOutputStream);
    jarOutputStream.closeEntry();
  }

  private void addFileToJar(final @NotNull JarOutputStream jarOutputStream, final @NotNull File file, @NotNull String relativePath,
                            final @NotNull THashSet<String> writtenPaths) throws IOException {
    if (!file.exists()) {
      return;
    }

    relativePath = addParentDirectories(jarOutputStream, writtenPaths, relativePath);
    myContext.getProgressIndicator().setText2(relativePath);
    ZipUtil.addFileToZip(jarOutputStream, file, relativePath, writtenPaths, myFileFilter);
  }

  private static String addParentDirectories(JarOutputStream jarOutputStream, THashSet<String> writtenPaths, String relativePath)
    throws IOException {
    while (relativePath.startsWith("/")) {
      relativePath = relativePath.substring(1);
    }
    int i = relativePath.indexOf('/');
    while (i != -1) {
      String prefix = relativePath.substring(0, i+1);
      if (!writtenPaths.contains(prefix) && prefix.length() > 1) {
        addEntry(jarOutputStream, prefix);
        writtenPaths.add(prefix);
      }
      i = relativePath.indexOf('/', i + 1);
    }
    return relativePath;
  }

  private static void addEntry(final ZipOutputStream output, @NonNls final String relativePath) throws IOException {
    ZipEntry e = new ZipEntry(relativePath);
    e.setMethod(ZipEntry.STORED);
    e.setSize(0);
    e.setCrc(0);
    output.putNextEntry(e);
    output.closeEntry();
  }

  private class JarsGraph implements GraphGenerator.SemiGraph<JarInfo> {
    public Collection<JarInfo> getNodes() {
      return myJarsToBuild;
    }

    public Iterator<JarInfo> getIn(final JarInfo n) {
      Set<JarInfo> ins = new HashSet<JarInfo>();
      for (JarDestinationInfo destination : n.getJarDestinations()) {
        ins.add(destination.getJarInfo());
      }
      return ins.iterator();
    }
  }
}
