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
import com.intellij.openapi.util.Ref;
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
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderLogger;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;
import org.jetbrains.jps.incremental.artifacts.ArtifactSourceToOutputMapping;
import org.jetbrains.jps.incremental.artifacts.IncArtifactBuilder;
import org.jetbrains.jps.incremental.artifacts.instructions.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

import java.io.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author nik
 */
public class JarsBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.packagingCompiler.JarsBuilder");
  private final Set<JarInfo> myJarsToBuild;
  private final CompileContext myContext;
  private Map<JarInfo, File> myBuiltJars;
  private final ArtifactSourceToOutputMapping mySrcOutMapping;
  private final ArtifactOutputToSourceMapping myOutSrcMapping;
  private final ArtifactInstructionsBuilder myInstructions;

  public JarsBuilder(Set<JarInfo> jarsToBuild,
                     CompileContext context,
                     ArtifactSourceToOutputMapping srcOutMapping,
                     ArtifactOutputToSourceMapping outSrcMapping, ArtifactInstructionsBuilder instructions) {
    mySrcOutMapping = srcOutMapping;
    myOutSrcMapping = outSrcMapping;
    myInstructions = instructions;
    DependentJarsEvaluator evaluator = new DependentJarsEvaluator();
    for (JarInfo jarInfo : jarsToBuild) {
      evaluator.addJarWithDependencies(jarInfo);
    }
    myJarsToBuild = evaluator.getJars();
    myContext = context;
  }

  public boolean buildJars() throws IOException, ProjectBuildException {
    myContext.processMessage(new ProgressMessage("Building archives..."));

    final JarInfo[] sortedJars = sortJars();
    if (sortedJars == null) {
      return false;
    }

    myBuiltJars = new HashMap<JarInfo, File>();
    try {
      for (JarInfo jar : sortedJars) {
        myContext.checkCanceled();
        buildJar(jar);
      }

      myContext.processMessage(new ProgressMessage("Copying archives..."));
      copyJars();
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

  private void copyJars() throws IOException {
    for (Map.Entry<JarInfo, File> entry : myBuiltJars.entrySet()) {
      File fromFile = entry.getValue();
      final JarInfo jarInfo = entry.getKey();
      DestinationInfo destination = jarInfo.getDestination();
      if (destination instanceof ExplodedDestinationInfo) {
        File toFile = new File(FileUtil.toSystemDependentName(destination.getOutputPath()));
        FileUtil.rename(fromFile, toFile);
      }
    }
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

  private void buildJar(final JarInfo jar) throws IOException {
    if (jar.getContent().isEmpty()) {
      final String message = "Archive '" + jar.getPresentableDestination() + "' has no files so it won't be created";
      myContext.processMessage(new CompilerMessage(IncArtifactBuilder.BUILDER_NAME, BuildMessage.Kind.WARNING, message));
      return;
    }

    myContext.processMessage(new ProgressMessage("Building " + jar.getPresentableDestination() + "..."));
    File jarFile = FileUtil.createTempFile("artifactCompiler", "tmp");
    myBuiltJars.put(jar, jarFile);

    FileUtil.createParentDirs(jarFile);
    final String targetJarPath = jar.getDestination().getOutputFilePath();
    Manifest manifest = loadManifest(jar, targetJarPath);
    final JarOutputStream jarOutputStream = createJarOutputStream(jarFile, manifest);

    try {
      final THashSet<String> writtenPaths = new THashSet<String>();
      if (manifest != null) {
        writtenPaths.add(JarFile.MANIFEST_NAME);
      }

      for (Pair<String, Object> pair : jar.getContent()) {
        final String relativePath = pair.getFirst();
        if (pair.getSecond() instanceof ArtifactSourceRoot) {
          final ArtifactSourceRoot root = (ArtifactSourceRoot)pair.getSecond();
          final int rootIndex = myInstructions.getRootIndex(root);
          LOG.assertTrue(rootIndex != -1, root + " not found in instructions");
          final ArtifactBuilderLogger logger = myContext.getLoggingManager().getArtifactBuilderLogger();
          if (root instanceof FileBasedArtifactSourceRoot) {
            addFileToJar(jarOutputStream, jarFile, root.getRootFile(), root.getFilter(), relativePath, targetJarPath, writtenPaths,
                         rootIndex);
          }
          else {
            final String filePath = FileUtil.toSystemIndependentName(root.getRootFile().getAbsolutePath());
            logger.fileCopied(filePath);
            mySrcOutMapping.appendData(filePath, Collections.singletonList(targetJarPath));
            myOutSrcMapping.appendData(targetJarPath, Collections
              .singletonList(new ArtifactOutputToSourceMapping.SourcePathAndRootIndex(filePath, rootIndex)));
            extractFileAndAddToJar(jarOutputStream, (JarBasedArtifactSourceRoot)root, relativePath, writtenPaths);
          }
        }
        else {
          JarInfo nestedJar = (JarInfo)pair.getSecond();
          File nestedJarFile = myBuiltJars.get(nestedJar);
          if (nestedJarFile != null) {
            addFileToJar(jarOutputStream, jarFile, nestedJarFile, SourceFileFilter.ALL, relativePath, targetJarPath, writtenPaths, -1);
          }
          else {
            LOG.debug("nested jar file " + relativePath + " for " + jar.getPresentableDestination() + " not found");
          }
        }
      }
    }
    finally {
      jarOutputStream.close();
    }
  }

  private static JarOutputStream createJarOutputStream(File jarFile, @Nullable Manifest manifest) throws IOException {
    final BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(jarFile));
    if (manifest != null) {
      return new JarOutputStream(outputStream, manifest);
    }
    return new JarOutputStream(outputStream);
  }

  @Nullable
  private Manifest loadManifest(JarInfo jar, String targetJarPath) throws IOException {
    for (Pair<String, Object> pair : jar.getContent()) {
      if (pair.getSecond() instanceof ArtifactSourceRoot) {
        final String rootPath = pair.getFirst();
        if (!JarFile.MANIFEST_NAME.startsWith(rootPath)) {
          continue;
        }
        final String manifestPath = JpsPathUtil.trimForwardSlashes(JarFile.MANIFEST_NAME.substring(rootPath.length()));
        final ArtifactSourceRoot root = (ArtifactSourceRoot)pair.getSecond();
        if (root instanceof FileBasedArtifactSourceRoot) {
          final File manifestFile = new File(root.getRootFile(), manifestPath);
          if (manifestFile.exists()) {
            final String fullManifestPath = FileUtil.toSystemIndependentName(manifestFile.getAbsolutePath());
            myContext.getLoggingManager().getArtifactBuilderLogger().fileCopied(fullManifestPath);
            mySrcOutMapping.appendData(fullManifestPath, Collections.singletonList(targetJarPath));
            //noinspection IOResourceOpenedButNotSafelyClosed
            return createManifest(new FileInputStream(manifestFile), manifestFile);
          }
        }
        else {
          final Ref<Manifest> manifestRef = Ref.create(null);
          ((JarBasedArtifactSourceRoot)root).processEntries(new JarBasedArtifactSourceRoot.EntryProcessor() {
            @Override
            public void process(@Nullable InputStream inputStream, @NotNull String relativePath) throws IOException {
              if (manifestRef.isNull() && relativePath.equals(manifestPath) && inputStream != null) {
                manifestRef.set(createManifest(inputStream, root.getRootFile()));
              }
            }
          });
          if (!manifestRef.isNull()) {
            return manifestRef.get();
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private Manifest createManifest(InputStream manifestStream, File manifestFile) {
    try {
      try {
        return new Manifest(manifestStream);
      }
      finally {
        manifestStream.close();
      }
    }
    catch (IOException e) {
      myContext.processMessage(new CompilerMessage(IncArtifactBuilder.BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   "Cannot create MANIFEST.MF from " + manifestFile.getAbsolutePath() + ":" + e.getMessage()));
      LOG.debug(e);
      return null;
    }
  }

  private static void extractFileAndAddToJar(final JarOutputStream jarOutputStream, final JarBasedArtifactSourceRoot root,
                                             final String relativeOutputPath, final Set<String> writtenPaths)
    throws IOException {
    final long timestamp = root.getRootFile().lastModified();
    root.processEntries(new JarBasedArtifactSourceRoot.EntryProcessor() {
      @Override
      public void process(@Nullable InputStream inputStream, @NotNull String relativePath) throws IOException {
        String pathInJar = addParentDirectories(jarOutputStream, writtenPaths, JpsPathUtil.appendToPath(relativeOutputPath, relativePath));

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
                            SourceFileFilter filter, @NotNull String relativePath, String targetJarPath,
                            final @NotNull Set<String> writtenPaths, final int rootIndex) throws IOException {
    if (!file.exists() || FileUtil.isAncestor(file, jarFile, false)) {
      return;
    }

    relativePath = addParentDirectories(jarOutputStream, writtenPaths, relativePath);
    addFileOrDirRecursively(jarOutputStream, file, filter, relativePath, targetJarPath, writtenPaths, rootIndex);
  }

  private void addFileOrDirRecursively(@NotNull ZipOutputStream jarOutputStream,
                                       @NotNull File file,
                                       SourceFileFilter filter,
                                       @NotNull String relativePath,
                                       String targetJarPath, @NotNull Set<String> writtenItemRelativePaths, int rootIndex) throws IOException {
    final String filePath = FileUtil.toSystemIndependentName(file.getAbsolutePath());
    if (!filter.accept(filePath, myContext.getProjectDescriptor().dataManager)) {
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
          addFileOrDirRecursively(jarOutputStream, child, filter, directoryPath + child.getName(), targetJarPath, writtenItemRelativePaths,
                                  rootIndex);
        }
      }
      return;
    }

    final boolean added = ZipUtil.addFileToZip(jarOutputStream, file, relativePath, writtenItemRelativePaths, null);
    if (rootIndex != -1) {
      myOutSrcMapping.appendData(targetJarPath, Collections.singletonList(new ArtifactOutputToSourceMapping.SourcePathAndRootIndex(filePath, rootIndex)));
      if (added) {
        mySrcOutMapping.appendData(filePath, Collections.singletonList(targetJarPath));
        myContext.getLoggingManager().getArtifactBuilderLogger().fileCopied(filePath);
      }
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
      final DestinationInfo destination = n.getDestination();
      if (destination instanceof JarDestinationInfo) {
        ins.add(((JarDestinationInfo)destination).getJarInfo());
      }
      return ins.iterator();
    }
  }
}
