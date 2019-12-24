// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisIndex;
import com.intellij.concurrency.JobLauncher;
import com.intellij.find.ngrams.TrigramIndex;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.JavaSimplePropertyIndex;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.todo.TodoIndex;
import com.intellij.psi.impl.java.JavaBinaryPlusExpressionIndex;
import com.intellij.psi.impl.java.JavaFunctionalExpressionIndex;
import com.intellij.psi.impl.java.stubs.index.JavaAutoModuleNameIndex;
import com.intellij.psi.impl.search.JavaNullMethodArgumentIndex;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.IndexableSetContributor;
import com.intellij.util.indexing.hash.HashBasedIndexGenerator;
import com.intellij.util.io.zip.JBZipEntry;
import com.intellij.util.io.zip.JBZipFile;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.index.stubs.StubHashBasedIndexGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

public class DumpIndexAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(DumpIndexAction.class);

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) return;
    Collection<IndexChunk> projectChunks = Arrays
            .stream(ModuleManager.getInstance(project).getModules())
            .flatMap(m -> IndexChunk.generate(m))
            .collect(Collectors.toMap(ch -> ch.getName(), ch -> ch, IndexChunk::mergeUnsafe))
            .values();

    Set<VirtualFile>
            additionalRoots = IndexableSetContributor.EP_NAME.extensions().flatMap(contributor -> Stream.concat(IndexableSetContributor.getRootsToIndex(contributor).stream(),
            IndexableSetContributor.getProjectRootsToIndex(contributor, project).stream())).collect(
            Collectors.toSet());

    Set<VirtualFile> synthRoots = new THashSet<>();
    for (AdditionalLibraryRootsProvider provider : AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
      for (SyntheticLibrary library : provider.getAdditionalProjectLibraries(project)) {
        for (VirtualFile root : library.getAllRoots()) {
          // do not try to visit under-content-roots because the first task took care of that already
          if (!ProjectFileIndex.getInstance(project).isInContent(root)) {
            synthRoots.add(root);
          }
        }
      }
    }

    List<IndexChunk> chunks = Stream.concat(projectChunks.stream(),
            Stream.of(new IndexChunk(additionalRoots, "ADDITIONAL"),
                    new IndexChunk(synthRoots, "SYNTH"))).collect(Collectors.toList());

    //IndexChunk chunk = chunks.stream().reduce((c1, c2) -> IndexChunk.mergeUnsafe(c1, c2)).get();

    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.withTitle("Select Index Dump Directory");
    VirtualFile file = FileChooser.chooseFile(descriptor, project, null);
    if (file != null) {
      ProgressManager.getInstance().run(new Task.Modal(project, "Exporting Indexes..." , true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          File out = VfsUtilCore.virtualToIoFile(file);
          FileUtil.delete(out);
          exportIndices(chunks, out, indicator, project);
        }
      });
    }
  }

  public static void exportIndices(@NotNull List<IndexChunk> chunks,
                                   @NotNull File out,
                                   @NotNull ProgressIndicator indicator,
                                   @NotNull Project project) {
    indicator.setIndeterminate(false);
    AtomicInteger idx = new AtomicInteger();
    if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(chunks, indicator, chunk -> {
      indicator.setText("Indexing '" + chunk.getName() + "' chunk");
      File chunkOut = new File(out, chunk.getName());
      ReadAction.run(() -> {
        Stream<HashBasedIndexGenerator<?, ?>> fbIndexes = getExportableIndices(true).map(ex -> new HashBasedIndexGenerator(ex, chunkOut));
        Stream<HashBasedIndexGenerator<?, ?>> stubIndex = Stream.of(new StubHashBasedIndexGenerator(chunkOut));
        List<HashBasedIndexGenerator<?, ?>> indexes = Stream.concat(fbIndexes, stubIndex).collect(Collectors.toList());
        HashBasedIndexGenerator.generate(chunk.getRoots(),indexes, project, chunkOut);
      });
      indicator.setFraction(((double) idx.incrementAndGet()) / chunks.size());
      return true;
    })) {
      throw new AssertionError();
    }

    indicator.setIndeterminate(true);
    indicator.setText("Zipping index pack");

    File zipFile = new File(out.getAbsolutePath() + ".zip");
    FileUtil.delete(zipFile);
    try (JBZipFile file = new JBZipFile(zipFile)) {
      Path outPath = out.toPath();
      Files.walk(outPath).forEach(p -> {
        if (Files.isDirectory(p)) return;
        String relativePath = outPath.relativize(p).toString();
        try {
          JBZipEntry entry = file.getOrCreateEntry(relativePath);
          entry.setMethod(ZipEntry.STORED);
          entry.setDataFromFile(p.toFile());
        }
        catch (IOException e) {
          LOG.error(e);
        }
      });
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @NotNull
  private static Stream<FileBasedIndexExtension> getExportableIndices(boolean all) {
    if (all) {
      return FileBasedIndexExtension
              .EXTENSION_POINT_NAME
              .extensions()
              .filter(ex -> ex.dependsOnFileContent())
              .filter(ex -> !(ex instanceof StubUpdatingIndex));
    }

    //kt
    Stream<FileBasedIndexExtension> ktIndices =
            FileBasedIndexExtension
                    .EXTENSION_POINT_NAME
                    .extensions()
                    .filter(id -> id.getName().getName().contains("kotlin"));

    Set<ID<Object, Object>> xmlIndexIds = ContainerUtil.set(ID.findByName("XmlTagNames"),
            ID.findByName("XmlNamespaces"),
            ID.findByName("SchemaTypeInheritance"),
            ID.findByName("DomFileIndex"),
            ID.findByName("xmlProperties"));
    //xml
    Stream<FileBasedIndexExtension> xmlIndices =
            FileBasedIndexExtension
                    .EXTENSION_POINT_NAME
                    .extensions()
                    .filter(id -> xmlIndexIds.contains(id.getName()));

    //base
    Stream<FileBasedIndexExtension> coreIndices =
            FileBasedIndexExtension
                    .EXTENSION_POINT_NAME
                    .extensions()
                    .filter(ex -> ex.getName().equals(TrigramIndex.INDEX_ID) ||
                            ex.getName().equals(TodoIndex.NAME) ||
                            ex.getName().equals(IdIndex.NAME) ||
                            ex.getName().getName().equals("HashFragmentIndex"));

    //java
    Stream<FileBasedIndexExtension> javaIndices =
            FileBasedIndexExtension
                    .EXTENSION_POINT_NAME
                    .extensions()
                    .filter(ex -> ex instanceof BytecodeAnalysisIndex ||
                            ex instanceof JavaAutoModuleNameIndex ||
                            ex instanceof JavaFunctionalExpressionIndex ||
                            ex instanceof JavaSimplePropertyIndex ||
                            ex instanceof JavaNullMethodArgumentIndex ||
                            ex instanceof JavaBinaryPlusExpressionIndex);

    return Stream.concat(Stream.concat(Stream.concat(coreIndices, javaIndices), xmlIndices), ktIndices);
  }

  private static final class IndexChunk {
    private final Set<VirtualFile> myRoots;
    private final String myName;

    IndexChunk(Set<VirtualFile> roots, String name) {
      myRoots = roots;
      myName = name;
    }

    private String getName() {
      return myName;
    }

    private Set<VirtualFile> getRoots() {
      return myRoots;
    }

    static IndexChunk mergeUnsafe(IndexChunk ch1, IndexChunk ch2) {
      ch1.getRoots().addAll(ch2.getRoots());
      return ch1;
    }

    static Stream<IndexChunk> generate(Module module) {
      Stream<IndexChunk> libChunks = Arrays.stream(ModuleRootManager.getInstance(module).getOrderEntries())
              .map(orderEntry -> {
                if (orderEntry instanceof LibraryOrSdkOrderEntry) {
                  VirtualFile[] sources = orderEntry.getFiles(OrderRootType.SOURCES);
                  VirtualFile[] classes = orderEntry.getFiles(OrderRootType.CLASSES);
                  String name = null;
                  if (orderEntry instanceof JdkOrderEntry) {
                    name = ((JdkOrderEntry)orderEntry).getJdkName();
                  }
                  else if (orderEntry instanceof LibraryOrderEntry) {
                    name = ((LibraryOrderEntry)orderEntry).getLibraryName();
                  }
                  if (name == null) {
                    name = "unknown";
                  }
                  return new IndexChunk(ContainerUtil.union(Arrays.asList(sources), Arrays.asList(classes)), reducePath(splitByDots(name)));
                }
                return null;
              })
              .filter(Objects::nonNull);

      Set<VirtualFile> roots =
              ContainerUtil.union(ContainerUtil.newTroveSet(ModuleRootManager.getInstance(module).getContentRoots()),
              ContainerUtil.newTroveSet(ModuleRootManager.getInstance(module).getSourceRoots()));
      Stream<IndexChunk> srcChunks = Stream.of(new IndexChunk(roots, getChunkName(module)));

      return Stream.concat(libChunks, srcChunks);
    }

    private static String getChunkName(Module module) {
      ModuleManager moduleManager = ModuleManager.getInstance(module.getProject());
      String[] path;
      if (moduleManager.hasModuleGroups()) {
        path = moduleManager.getModuleGroupPath(module);
        assert path != null;
      } else {
        path = splitByDots(module.getName());
      }
      return reducePath(path);
    }

    @NotNull
    private static String reducePath(String[] path) {
      String[] reducedPath = Arrays.copyOfRange(path, 0, Math.min(1, path.length));
      return StringUtil.join(reducedPath, ".");
    }

    private static String[] splitByDots(String name) {
      return name.split("[-|:.]");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      IndexChunk chunk = (IndexChunk)o;
      return Objects.equals(myRoots, chunk.myRoots) &&
              Objects.equals(myName, chunk.myName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myRoots, myName);
    }
  }
}
