// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import com.intellij.util.graph.InboundSemiGraph;
import com.intellij.util.io.ZipUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.FSOperations;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;
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

public class JarsBuilder {
  private static final Logger LOG = Logger.getInstance(JarsBuilder.class);
  private final Set<JarInfo> myJarsToBuild;
  private final CompileContext myContext;
  private Map<JarInfo, File> myBuiltJars;
  private final BuildOutputConsumer myOutputConsumer;
  private final ArtifactOutputToSourceMapping myOutSrcMapping;

  public JarsBuilder(Set<JarInfo> jarsToBuild, CompileContext context, BuildOutputConsumer outputConsumer,
                     ArtifactOutputToSourceMapping outSrcMapping) {
    myOutputConsumer = outputConsumer;
    myOutSrcMapping = outSrcMapping;
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

    myBuiltJars = new HashMap<>();
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

  private JarInfo @Nullable [] sortJars() {
    final DFSTBuilder<JarInfo> builder = new DFSTBuilder<>(GraphGenerator.generate(CachingSemiGraph.cache(new JarsGraph())));
    if (!builder.isAcyclic()) {
      final Pair<JarInfo, JarInfo> dependency = builder.getCircularDependency();
      String message = "Cannot build: circular dependency found between '" + dependency.getFirst().getPresentableDestination() +
                       "' and '" + dependency.getSecond().getPresentableDestination() + "'";
      myContext.processMessage(new CompilerMessage(IncArtifactBuilder.BUILDER_NAME, BuildMessage.Kind.ERROR, message));
      return null;
    }

    JarInfo[] jars = myJarsToBuild.toArray(new JarInfo[0]);
    Arrays.sort(jars, builder.comparator());
    jars = ArrayUtil.reverseArray(jars);
    return jars;
  }

  private void buildJar(final JarInfo jar) throws IOException {
    final String emptyArchiveMessage = "Archive '" + jar.getPresentableDestination() + "' doesn't contain files so it won't be created";
    if (jar.getContent().isEmpty()) {
      myContext.processMessage(new CompilerMessage(IncArtifactBuilder.BUILDER_NAME, BuildMessage.Kind.WARNING, emptyArchiveMessage));
      return;
    }

    myContext.processMessage(new ProgressMessage("Building " + jar.getPresentableDestination() + "..."));
    File jarFile = FileUtil.createTempFile("artifactCompiler", "tmp");
    myBuiltJars.put(jar, jarFile);

    FileUtil.createParentDirs(jarFile);
    final String targetJarPath = jar.getDestination().getOutputFilePath();
    List<String> packedFilePaths = new ArrayList<>();
    Manifest manifest = loadManifest(jar, packedFilePaths);
    final JarOutputStream jarOutputStream = createJarOutputStream(jarFile, manifest);

    final THashSet<String> writtenPaths = new THashSet<>();
    try {
      if (manifest != null) {
        writtenPaths.add(JarFile.MANIFEST_NAME);
      }

      for (Pair<String, Object> pair : jar.getContent()) {
        final String relativePath = pair.getFirst();
        if (pair.getSecond() instanceof ArtifactRootDescriptor) {
          final ArtifactRootDescriptor descriptor = (ArtifactRootDescriptor)pair.getSecond();
          final int rootIndex = descriptor.getRootIndex();
          if (descriptor instanceof FileBasedArtifactRootDescriptor) {
            addFileToJar(jarOutputStream, jarFile, descriptor.getRootFile(), descriptor.getFilter(), relativePath, targetJarPath, writtenPaths,
                         packedFilePaths, rootIndex);
          }
          else {
            final String filePath = FileUtil.toSystemIndependentName(descriptor.getRootFile().getAbsolutePath());
            packedFilePaths.add(filePath);
            myOutSrcMapping.appendData(targetJarPath, rootIndex, filePath);
            extractFileAndAddToJar(jarOutputStream, (JarBasedArtifactRootDescriptor)descriptor, relativePath, writtenPaths);
          }
        }
        else {
          JarInfo nestedJar = (JarInfo)pair.getSecond();
          File nestedJarFile = myBuiltJars.get(nestedJar);
          if (nestedJarFile != null) {
            addFileToJar(jarOutputStream, jarFile, nestedJarFile, SourceFileFilter.ALL, relativePath, targetJarPath, writtenPaths,
                         packedFilePaths, -1);
          }
          else {
            LOG.debug("nested JAR file " + relativePath + " for " + jar.getPresentableDestination() + " not found");
          }
        }
      }

      if (writtenPaths.isEmpty()) {
        myContext.processMessage(new CompilerMessage(IncArtifactBuilder.BUILDER_NAME, BuildMessage.Kind.WARNING, emptyArchiveMessage));
        return;
      }

      final ProjectBuilderLogger logger = myContext.getLoggingManager().getProjectBuilderLogger();
      if (logger.isEnabled()) {
        logger.logCompiledPaths(packedFilePaths, IncArtifactBuilder.BUILDER_NAME, "Packing files:");
      }
      myOutputConsumer.registerOutputFile(new File(targetJarPath), packedFilePaths);

    }
    finally {
      if (writtenPaths.isEmpty()) {
        try {
          jarOutputStream.close();
        }
        catch (IOException ignored) {
        }
        FileUtil.delete(jarFile);
        myBuiltJars.remove(jar);
      }
      else {
        try {
          jarOutputStream.close();
        }
        catch (IOException e) {
          String messageText = "Cannot create '" + jar.getPresentableDestination() + "': " + e.getMessage();
          myContext.processMessage(new CompilerMessage(IncArtifactBuilder.BUILDER_NAME, BuildMessage.Kind.ERROR, messageText));
          LOG.debug(e);
        }
      }
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
  private Manifest loadManifest(JarInfo jar, List<? super String> packedFilePaths) throws IOException {
    for (Pair<String, Object> pair : jar.getContent()) {
      if (pair.getSecond() instanceof ArtifactRootDescriptor) {
        final String rootPath = pair.getFirst();
        if (!JarFile.MANIFEST_NAME.startsWith(rootPath)) {
          continue;
        }
        final String manifestPath = JpsArtifactPathUtil.trimForwardSlashes(JarFile.MANIFEST_NAME.substring(rootPath.length()));
        final ArtifactRootDescriptor descriptor = (ArtifactRootDescriptor)pair.getSecond();
        if (descriptor instanceof FileBasedArtifactRootDescriptor) {
          final File manifestFile = new File(descriptor.getRootFile(), manifestPath);
          if (manifestFile.exists()) {
            final String fullManifestPath = FileUtil.toSystemIndependentName(manifestFile.getAbsolutePath());
            packedFilePaths.add(fullManifestPath);
            try (FileInputStream stream = new FileInputStream(manifestFile)) {
              return createManifest(stream, manifestFile);
            }
          }
        }
        else {
          final Ref<Manifest> manifestRef = Ref.create(null);
          ((JarBasedArtifactRootDescriptor)descriptor).processEntries(new JarBasedArtifactRootDescriptor.EntryProcessor() {
            @Override
            public void process(@Nullable InputStream inputStream, @NotNull String relativePath, ZipEntry entry) throws IOException {
              if (manifestRef.isNull() && relativePath.equals(manifestPath) && inputStream != null) {
                try (InputStream stream = inputStream) {
                  manifestRef.set(createManifest(stream, descriptor.getRootFile()));
                }
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
      return new Manifest(manifestStream);
    }
    catch (IOException e) {
      myContext.processMessage(new CompilerMessage(IncArtifactBuilder.BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   "Cannot create MANIFEST.MF from " + manifestFile.getAbsolutePath() + ":" + e.getMessage()));
      LOG.debug(e);
      return null;
    }
  }

  private void extractFileAndAddToJar(final JarOutputStream jarOutputStream, final JarBasedArtifactRootDescriptor root,
                                      final String relativeOutputPath, final Set<? super String> writtenPaths)
    throws IOException {
    final long timestamp = FSOperations.lastModified(root.getRootFile());
    root.processEntries(new JarBasedArtifactRootDescriptor.EntryProcessor() {
      @Override
      public void process(@Nullable InputStream inputStream, @NotNull String relativePath, ZipEntry entry) throws IOException {
        String pathInJar = addParentDirectories(jarOutputStream, writtenPaths, JpsArtifactPathUtil
          .appendToPath(relativeOutputPath, relativePath));

        if (inputStream == null) {
          if (!pathInJar.endsWith("/")) {
            addDirectoryEntry(jarOutputStream, pathInJar + "/", writtenPaths);
          }
        }
        else if (writtenPaths.add(pathInJar)) {
          ZipEntry newEntry = new ZipEntry(pathInJar);
          newEntry.setTime(timestamp);
          if (entry.getMethod() == ZipEntry.STORED) {
            newEntry.setMethod(ZipEntry.STORED);
            newEntry.setSize(entry.getSize());
            newEntry.setCrc(entry.getCrc());
          }
          jarOutputStream.putNextEntry(newEntry);
          FileUtil.copy(inputStream, jarOutputStream);
          try {
            jarOutputStream.closeEntry();
          }
          catch (IOException e) {
            String messageText = "Cannot extract '" + pathInJar + "' from '" + root.getRootFile().getAbsolutePath() + "' while building '" +
                                 root.getTarget().getArtifact().getName() + "' artifact: " + e.getMessage();
            myContext.processMessage(new CompilerMessage(IncArtifactBuilder.BUILDER_NAME, BuildMessage.Kind.ERROR, messageText));
            LOG.debug(e);
          }
        }
      }
    });

  }

  private void addFileToJar(final @NotNull JarOutputStream jarOutputStream, final @NotNull File jarFile, @NotNull File file,
                            SourceFileFilter filter, @NotNull String relativePath, String targetJarPath,
                            final @NotNull Set<? super String> writtenPaths, List<? super String> packedFilePaths, final int rootIndex) throws IOException {
    if (!file.exists() || FileUtil.isAncestor(file, jarFile, false)) {
      return;
    }

    relativePath = addParentDirectories(jarOutputStream, writtenPaths, relativePath);
    addFileOrDirRecursively(jarOutputStream, file, filter, relativePath, targetJarPath, writtenPaths, packedFilePaths, rootIndex);
  }

  private void addFileOrDirRecursively(@NotNull ZipOutputStream jarOutputStream,
                                       @NotNull File file,
                                       SourceFileFilter filter,
                                       @NotNull String relativePath,
                                       String targetJarPath,
                                       @NotNull Set<? super String> writtenItemRelativePaths,
                                       List<? super String> packedFilePaths,
                                       int rootIndex) throws IOException {
    final String filePath = FileUtil.toSystemIndependentName(file.getAbsolutePath());
    if (!filter.accept(filePath) || !filter.shouldBeCopied(filePath, myContext.getProjectDescriptor())) {
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
                                  packedFilePaths, rootIndex);
        }
      }
      return;
    }

    final boolean added = ZipUtil.addFileToZip(jarOutputStream, file, relativePath, writtenItemRelativePaths, null);
    if (rootIndex != -1) {
      myOutSrcMapping.appendData(targetJarPath, rootIndex, filePath);
      if (added) {
        packedFilePaths.add(filePath);
      }
    }
  }


  private static String addParentDirectories(JarOutputStream jarOutputStream, Set<? super String> writtenPaths, String relativePath) throws IOException {
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

  private static void addDirectoryEntry(final ZipOutputStream output, @NonNls final String relativePath, Set<? super String> writtenPaths) throws IOException {
    if (!writtenPaths.add(relativePath)) return;

    ZipEntry e = new ZipEntry(relativePath);
    e.setMethod(ZipEntry.STORED);
    e.setSize(0);
    e.setCrc(0);
    output.putNextEntry(e);
    output.closeEntry();
  }

  private class JarsGraph implements InboundSemiGraph<JarInfo> {
    @Override
    @NotNull
    public Collection<JarInfo> getNodes() {
      return myJarsToBuild;
    }

    @NotNull
    @Override
    public Iterator<JarInfo> getIn(final JarInfo n) {
      Set<JarInfo> ins = new HashSet<>();
      final DestinationInfo destination = n.getDestination();
      if (destination instanceof JarDestinationInfo) {
        ins.add(((JarDestinationInfo)destination).getJarInfo());
      }
      return ins.iterator();
    }
  }
}