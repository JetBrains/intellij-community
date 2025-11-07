// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder;

import com.intellij.tools.build.bazel.jvmIncBuilder.impl.*;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.forms.FormBinding;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph.PersistentMVStoreMapletFactory;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import com.sun.nio.file.ExtendedOpenOption;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.OffHeapStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.DependencyGraph;
import org.jetbrains.jps.dependency.GraphConfiguration;
import org.jetbrains.jps.dependency.impl.DependencyGraphImpl;
import org.jetbrains.jps.dependency.kotlin.LookupsIndex;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.jetbrains.jps.util.Iterators.*;

public class StorageManager implements CloseableExt {
  private final BuildContext myContext;
  private GraphConfiguration myGraphConfig;
  private ZipOutputBuilderImpl myOutputBuilder;
  private ZipOutputBuilderImpl myAbiOutputBuilder;
  private CompositeZipOutputBuilder myComposite;
  private InstrumentationClassFinder myInstrumentationClassFinder;
  private FormBinding myFormBinding;
  private boolean isKotlinCriDataGenerationEnabled;

  private final MVStore myDataSwapStore;

  public StorageManager(BuildContext context) {
    myContext = context;
    myDataSwapStore = new MVStore.Builder()
      .fileStore(new OffHeapStore())
      .autoCommitDisabled()
      .cacheSize(8)
      .open();
    myDataSwapStore.setVersionsToKeep(0);
    isKotlinCriDataGenerationEnabled = myContext.getKotlinCriStoragePath() != null;
  }

  public void cleanBuildState() throws IOException {
    closeDataStorages(false);
    Path output = myContext.getOutputZip();
    Path abiOutput = myContext.getAbiOutputZip();

    BuildProcessLogger logger = myContext.getBuildLogger();
    if (logger.isEnabled() && !myContext.isRebuild()) {
      // need this for tests
      Path outBackup = DataPaths.getJarBackupStoreFile(myContext, output);
      try (var is = new BufferedInputStream(Files.newInputStream(Files.exists(outBackup)? outBackup : output))) {
        List<String> paths = collect(filter(map(new ZipEntryIterator(is), ze -> ze.getEntry().getName()), n -> !n.endsWith("/")), new ArrayList<>());
        if (!paths.isEmpty()) {
          logger.logDeletedPaths(paths);
        }
      }
      catch (IOException ignored) {
        // ignore corrupted or non-existing zips
      }
    }

    Utils.deleteIfExists(output);
    if (abiOutput != null) {
      Utils.deleteIfExists(abiOutput);
    }

    deleteOrMoveRecursively(myContext.getDataDir(), DataPaths.getTrashDir(myContext));
  }

  public void cleanTrashDir() throws IOException {
    deleteRecursively(DataPaths.getTrashDir(myContext));
  }

  public static Path cleanDir(Path dir) throws IOException {
    if (Files.exists(dir)) {
      try (var files = Files.list(dir)) {
        for (Path file : files.toList()) {
          Utils.deleteIfExists(file);
        }
      }
    }
    return dir;
  }

  public <K, V> Map<K, V> createOffHeapMap(String name) {
    return myDataSwapStore.openMap(name);
  }

  public FormBinding getFormsBinding() throws Exception {
    FormBinding binding = myFormBinding;
    if (binding == null) {
      myFormBinding = binding = FormBinding.create(myContext);
    }
    return binding;
  }

  public BuildContext getContext() {
    return myContext;
  }

  @NotNull
  public GraphConfiguration getGraphConfiguration() throws IOException {
    if (myGraphConfig != null) {
      return myGraphConfig;
    }

    DependencyGraphImpl graph = createDependencyGraph();
    myGraphConfig = GraphConfiguration.create(graph, myContext.getPathMapper());
    return myGraphConfig;
  }

  @NotNull
  private DependencyGraphImpl createDependencyGraph() throws IOException {
    var filePath = DataPaths.getDepGraphStoreFile(myContext).toString();
    int maxBuilderThreads = Math.min(8, Runtime.getRuntime().availableProcessors());
    var containerFactory = new PersistentMVStoreMapletFactory(filePath, maxBuilderThreads);

    if (isKotlinCriDataGenerationEnabled) {
      return new DependencyGraphImpl(
        containerFactory,
        DependencyGraphImpl.IndexFactory.create(LookupsIndex::new)
      );
    } else {
      return new DependencyGraphImpl(containerFactory);
    }
  }

  @NotNull
  public ZipOutputBuilderImpl getOutputBuilder() throws IOException {
    ZipOutputBuilderImpl builder = myOutputBuilder;
    if (builder == null) {
      Path output = myContext.getOutputZip();
      Path previousOutput = DataPaths.getJarBackupStoreFile(myContext, output);
      myOutputBuilder = builder = new ZipOutputBuilderImpl(createOffHeapMap(output.getFileName().toString()), previousOutput, output, true);
    }
    return builder;
  }

  @Nullable
  public ZipOutputBuilderImpl getAbiOutputBuilder() throws IOException {
    ZipOutputBuilderImpl builder = myAbiOutputBuilder;
    if (builder == null) {
      Path abiOutputPath = myContext.getAbiOutputZip();
      if (abiOutputPath != null) {
        Path previousAbiOutput = DataPaths.getJarBackupStoreFile(myContext, abiOutputPath);
        myAbiOutputBuilder = builder = new ZipOutputBuilderImpl(createOffHeapMap(abiOutputPath.getFileName().toString()), previousAbiOutput, abiOutputPath, false);
      }
    }
    return builder;
  }

  public ZipOutputBuilder getCompositeOutputBuilder() throws IOException {
    CompositeZipOutputBuilder composite = myComposite;
    if (composite == null) {
      myComposite = composite = new CompositeZipOutputBuilder(getOutputBuilder(), getAbiOutputBuilder());
    }
    return composite;
  }

  public @NotNull InstrumentationClassFinder getInstrumentationClassFinder() throws MalformedURLException {
    InstrumentationClassFinder finder = myInstrumentationClassFinder;
    if (finder == null) {
      myInstrumentationClassFinder = finder = createInstrumentationClassFinder(jvmClassName -> {
        try {
          return getOutputBuilder().getContent(jvmClassName + ".class");
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }
    return finder;
  }

  public DependencyGraph getGraph() throws IOException {
    return getGraphConfiguration().getGraph();
  }

  @Override
  public final void close() {
    close(true); // close saving all successfully compiled content
  }

  @Override
  public void close(boolean saveChanges) {
    try {
      closeDataStorages(saveChanges);
    }
    finally {
      myDataSwapStore.rollback();
      if (myDataSwapStore.getFileStore() instanceof OffHeapStore store) {
        store.truncate(0); // forcibly clean byte buffers
      }
      myDataSwapStore.close();
    }
  }

  private void closeDataStorages(boolean saveChanges) {
    GraphConfiguration config = myGraphConfig;
    if (config != null) {
      myGraphConfig = null;
        writeKotlinCriData(config.getGraph(), saveChanges);
      safeClose(config.getGraph(), saveChanges);
    }

    myComposite = null;

    safeClose(myOutputBuilder, saveChanges);
    myOutputBuilder = null;

    safeClose(myAbiOutputBuilder, saveChanges);
    myAbiOutputBuilder = null;

    InstrumentationClassFinder finder = myInstrumentationClassFinder;
    if (finder != null) {
      myInstrumentationClassFinder = null;
      finder.releaseResources();
    }
  }

  private void writeKotlinCriData(DependencyGraph graph, Boolean saveChanges) {
    if (!saveChanges || !isKotlinCriDataGenerationEnabled) return;
    Path kotlinCriPath = myContext.getKotlinCriStoragePath();
    if (kotlinCriPath == null) return;
    if (!Files.exists(kotlinCriPath)) {
      try { Files.createDirectories(kotlinCriPath); }
      catch (IOException e) { myContext.report(Message.create(null, e)); }
    }

    KotlinCriUtilKt.prepareSerializedData(graph)
      .forEach(
        (name, content) -> {
          try {
            Files.write(kotlinCriPath.resolve(name), content,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
          }
          catch (IOException e) {
            myContext.report(Message.create(null, e));
          }
        }
      );
  }

  private void safeClose(Closeable cl, boolean saveChanges) {
    try {
      if (cl instanceof CloseableExt) {
        ((CloseableExt) cl).close(saveChanges);
      }
      else if (cl != null) {
        cl.close();
      }
    }
    catch (Throwable e) {
      myContext.report(Message.create(null, e));
    }
  }

  private InstrumentationClassFinder createInstrumentationClassFinder(Function<String, byte[]> outputContentLookup) throws MalformedURLException {
    final URL jrt = tryGetJrtURL();
    List<URL> platformCp = jrt != null? List.of(jrt) : List.of();
    final List<URL> urls = new ArrayList<>();
    for (Path path : map(myContext.getBinaryDependencies().getElements(), myContext.getPathMapper()::toPath)) {
      urls.add(path.toUri().toURL());
    }
    return new InstrumentationClassFinder(platformCp.toArray(URL[]::new), urls.toArray(URL[]::new)) {
      @Override
      protected InputStream lookupClassBeforeClasspath(String internalClassName) {
        final byte[] content = outputContentLookup.apply(internalClassName);
        return content != null? new ByteArrayInputStream(content) : null;
      }
    };
  }

  private static URL tryGetJrtURL() {
    final String home = System.getProperty("java.home");
    Path jrtFsPath = Path.of(home).normalize().resolve("lib").resolve("jrt-fs.jar");
    if (Files.isRegularFile(jrtFsPath)) {
      // this is a modular jdk where platform classes are stored in a jrt-fs image
      try {
        return InstrumentationClassFinder.createJDKPlatformUrl(home);
      }
      catch (MalformedURLException ignored) {
      }
    }
    return null;
  }

  private static void createLinkAfterCopy(Path linkFile, Path originalFile, Path tempDir) throws IOException {
    int index = 1;
    Path copyFile;
    do {
      copyFile = tempDir.resolve(linkFile.getFileName().toString() + "-" + index++);
      if (!Files.exists(copyFile)) {
        Path tempFile = Files.createTempFile(tempDir, null, null);
        try {
          try (OutputStream out = Files.newOutputStream(tempFile, ExtendedOpenOption.NOSHARE_WRITE)) {
            Files.copy(originalFile, out);
          }

          try {
            Files.move(tempFile, copyFile, StandardCopyOption.ATOMIC_MOVE);
            tempFile = null;
          }
          catch (AccessDeniedException ignored) {
            // ATOMIC_MOVE uses MOVEFILE_REPLACE_EXISTING, ignore
          }
          catch (FileAlreadyExistsException ignored) {
            // ignore
          }
        }
        finally {
          if (tempFile != null) {
            Files.delete(tempFile);
          }
        }
      }
    } while (!Utils.tryCreateLink(linkFile, copyFile));
  }

  public static void backupDependencies(BuildContext context, Iterable<Path> deletedPaths, Iterable<Path> presentPaths) throws IOException {
    Files.createDirectories(DataPaths.getDependenciesBackupStoreDir(context));

    for (Path deletedOrModified : flat(deletedPaths, presentPaths)) {
      Path backup = DataPaths.getJarBackupStoreFile(context, deletedOrModified);
      if (!Utils.deleteIfExists(backup) && Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
        Path trash = DataPaths.getTrashDir(context);
        Files.createDirectories(trash);
        Path tempFile = Files.createTempFile(trash, null, null);
        Files.move(backup, tempFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      }
    }

    for (Path presentPath : presentPaths) {
      Path backup = DataPaths.getJarBackupStoreFile(context, presentPath);
      if (!Utils.tryCreateLink(backup, presentPath)) {
        Path trash = DataPaths.getLibraryTrashDir(context, presentPath);
        Files.createDirectories(trash);
        createLinkAfterCopy(backup, presentPath, trash);
      }
    }
  }


  private static void deleteOrMoveRecursively(Path dataDir, Path trashDir) throws IOException {
    if (Files.notExists(dataDir)) {
      return;
    }

    Files.walkFileTree(dataDir, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (!Utils.deleteIfExists(file) && !file.startsWith(trashDir) && Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
          Files.createDirectories(trashDir);
          Path tempFile = Files.createTempFile(trashDir, null, null);
          Files.move(file, tempFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        if (exc != null) {
          throw exc;
        }

        try {
          Utils.deleteIfExists(dir);
        }
        catch (DirectoryNotEmptyException e) {
          if (dir.equals(trashDir) || (Files.exists(trashDir) && dir.equals(dataDir))) {
            // ignore
          }
          else {
            throw e;
          }
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private static void deleteRecursively(Path dataDir) throws IOException {
    if (Files.exists(dataDir)) {
      Files.walkFileTree(dataDir, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Utils.deleteIfExists(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          if (exc != null) {
            throw exc;
          }
          try {
            Utils.deleteIfExists(dir);
          }
          catch (DirectoryNotEmptyException ignore) {
          }
          return FileVisitResult.CONTINUE;
        }
      });
    }
  }
}
