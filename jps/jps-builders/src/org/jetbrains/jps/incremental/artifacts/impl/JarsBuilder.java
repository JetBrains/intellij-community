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

package org.jetbrains.jps.incremental.artifacts.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.io.ZipUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.PathUtil;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderLogger;
import org.jetbrains.jps.incremental.artifacts.IncArtifactBuilder;
import org.jetbrains.jps.incremental.artifacts.instructions.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

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
    myContext.processMessage(new ProgressMessage("Building archives..."));

    final JarInfo[] sortedJars = sortJars();
    if (sortedJars == null) {
      return false;
    }

    myBuiltJars = new HashMap<JarInfo, File>();
    try {
      for (JarInfo jar : sortedJars) {
        buildJar(jar);
      }

      myContext.processMessage(new ProgressMessage("Copying archives..."));
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
            FileUtil.copyContent(fromFile, toFile);
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
      String message = "Cannot build: circular dependency found between '" + dependency.getFirst().getPresentableDestination() +
                       "' and '" + dependency.getSecond().getPresentableDestination() + "'";
      myContext.processMessage(new CompilerMessage(IncArtifactBuilder.BUILDER_NAME, BuildMessage.Kind.ERROR, message));
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
    if (jar.getPackedJars().isEmpty() && jar.getPackedRoots().isEmpty()) {
      final String message = "Archive '" + jar.getPresentableDestination() + "' has no files so it won't be created";
      myContext.processMessage(new CompilerMessage(IncArtifactBuilder.BUILDER_NAME, BuildMessage.Kind.WARNING, message));
      return;
    }

    myContext.processMessage(new ProgressMessage("Building " + jar.getPresentableDestination() + "..."));
    File jarFile = FileUtil.createTempFile("artifactCompiler", "tmp");
    myBuiltJars.put(jar, jarFile);

    FileUtil.createParentDirs(jarFile);
    final JarOutputStream jarOutputStream = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(jarFile)));

    try {
      final THashSet<String> writtenPaths = new THashSet<String>();
      for (Pair<String, ArtifactSourceRoot> pair : jar.getPackedRoots()) {
        final ArtifactSourceRoot root = pair.getSecond();
        final ArtifactBuilderLogger logger = myContext.getLoggingManager().getArtifactBuilderLogger();
        if (root instanceof FileBasedArtifactSourceRoot) {
          addFileToJar(jarOutputStream, jarFile, root.getRootFile(), root.getFilter(), pair.getFirst(), writtenPaths);
        }
        else {
          logger.fileCopied(FileUtil.toSystemIndependentName(root.getRootFile().getAbsolutePath()));
          extractFileAndAddToJar(jarOutputStream, (JarBasedArtifactSourceRoot)root, pair.getFirst(), writtenPaths);
        }
      }

      for (Pair<String, JarInfo> nestedJar : jar.getPackedJars()) {
        File nestedJarFile = myBuiltJars.get(nestedJar.getSecond());
        if (nestedJarFile != null) {
          addFileToJar(jarOutputStream, jarFile, nestedJarFile, SourceFileFilter.ALL, nestedJar.getFirst(), writtenPaths);
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

  private static void extractFileAndAddToJar(final JarOutputStream jarOutputStream, final JarBasedArtifactSourceRoot root,
                                             final String relativeOutputPath, final Set<String> writtenPaths)
    throws IOException {
    final long timestamp = root.getRootFile().lastModified();
    root.processEntries(new JarBasedArtifactSourceRoot.EntryProcessor() {
      @Override
      public void process(@Nullable InputStream inputStream, @NotNull String relativePath) throws IOException {
        String pathInJar = addParentDirectories(jarOutputStream, writtenPaths, PathUtil.appendToPath(relativeOutputPath, relativePath));

        if (inputStream == null) {
          addDirectoryEntry(jarOutputStream, pathInJar + "/", writtenPaths);
        }
        else if (writtenPaths.add(pathInJar)) {
          ZipEntry entry = new ZipEntry(pathInJar);
          entry.setTime(timestamp);
          jarOutputStream.putNextEntry(entry);
          FileUtil.copy(inputStream, jarOutputStream);
          jarOutputStream.closeEntry();
        }
      }
    });

  }

  private void addFileToJar(final @NotNull JarOutputStream jarOutputStream, final @NotNull File jarFile, @NotNull File file,
                            SourceFileFilter filter, @NotNull String relativePath, final @NotNull Set<String> writtenPaths) throws IOException {
    if (!file.exists() || FileUtil.isAncestor(file, jarFile, false)) {
      return;
    }

    relativePath = addParentDirectories(jarOutputStream, writtenPaths, relativePath);
    addFileOrDirRecursively(jarOutputStream, file, filter, relativePath, writtenPaths);
  }

  private void addFileOrDirRecursively(@NotNull ZipOutputStream jarOutputStream,
                                       @NotNull File file,
                                       SourceFileFilter filter,
                                       @NotNull String relativePath,
                                       @NotNull Set<String> writtenItemRelativePaths) throws IOException {
    if (!filter.accept(FileUtil.toSystemIndependentName(file.getAbsolutePath()))) {
      return;
    }

    if (file.isDirectory()) {
      final String directoryPath = relativePath.length() == 0 ? "" : relativePath + "/";
      if (!directoryPath.isEmpty()) {
        addDirectoryEntry(jarOutputStream, directoryPath, writtenItemRelativePaths);
      }
      final File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          addFileOrDirRecursively(jarOutputStream, child, filter, directoryPath + child.getName(), writtenItemRelativePaths);
        }
      }
      return;
    }

    final boolean added = ZipUtil.addFileToZip(jarOutputStream, file, relativePath, writtenItemRelativePaths, myFileFilter);
    if (added) {
      myContext.getLoggingManager().getArtifactBuilderLogger().fileCopied(FileUtil.toSystemIndependentName(file.getAbsolutePath()));
    }
  }


  private static String addParentDirectories(JarOutputStream jarOutputStream, Set<String> writtenPaths, String relativePath) throws IOException {
    while (StringUtil.startsWithChar(relativePath, '/')) {
      relativePath = relativePath.substring(1);
    }
    int i = relativePath.indexOf('/');
    while (i != -1) {
      String prefix = relativePath.substring(0, i+1);
      if (prefix.length() > 1) {
        addDirectoryEntry(jarOutputStream, prefix, writtenPaths);
      }
      i = relativePath.indexOf('/', i + 1);
    }
    return relativePath;
  }

  private static void addDirectoryEntry(final ZipOutputStream output, @NonNls final String relativePath, Set<String> writtenPaths) throws IOException {
    if (!writtenPaths.add(relativePath)) return;

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
