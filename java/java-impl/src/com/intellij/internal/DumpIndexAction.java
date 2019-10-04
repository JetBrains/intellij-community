// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisIndex;
import com.intellij.find.ngrams.TrigramIndex;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileUrlChangeAdapter;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.java.stubs.index.JavaAutoModuleNameIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.IndexableSetContributor;
import com.intellij.util.indexing.hash.HashBasedIndexGenerator;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.index.stubs.StubHashBasedIndexGenerator;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DumpIndexAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) return;
    Stream<VirtualFile> libRoots = Arrays
      .stream(ModuleManager.getInstance(project).getModules())
      .flatMap(m -> Arrays.stream(ModuleRootManager.getInstance(m).getOrderEntries()))
      .filter(e -> e instanceof LibraryOrSdkOrderEntry)
      .flatMap(e -> Stream.concat(Arrays.stream(e.getFiles(OrderRootType.CLASSES)), Arrays.stream(e.getFiles(OrderRootType.SOURCES))));

    Stream<VirtualFile> additionalRoots = IndexableSetContributor.EP_NAME.extensions().flatMap(contributor -> Stream.concat(IndexableSetContributor.getRootsToIndex(contributor).stream(),
                                                                                                                            IndexableSetContributor.getProjectRootsToIndex(contributor, project).stream()));
    Set<VirtualFile> roots = Stream.concat(libRoots, additionalRoots).collect(Collectors.toSet());

    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.withTitle("Select Index Dump Directory");
    VirtualFile file = FileChooser.chooseFile(descriptor, project, null);
    if (file == null) return;
    File out = VfsUtilCore.virtualToIoFile(file);
    FileUtil.delete(out);
    exportIndices(roots, out);
  }

  public static void exportIndices(@NotNull Set<VirtualFile> roots, @NotNull File out) {
    StubHashBasedIndexGenerator generator = new StubHashBasedIndexGenerator(out);
    generator.generate(roots);

    getExportableIndices().forEach(ex -> {
      HashBasedIndexGenerator indexGenerator = new HashBasedIndexGenerator(ex, out);
      indexGenerator.generate(roots);
    });
  }

  @NotNull
  private static Stream<FileBasedIndexExtension> getExportableIndices() {
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
                      ex.getName().equals(IdIndex.NAME));

    //java
    Stream<FileBasedIndexExtension> javaIndices =
      FileBasedIndexExtension
        .EXTENSION_POINT_NAME
        .extensions()
        .filter(ex -> ex instanceof BytecodeAnalysisIndex || ex instanceof JavaAutoModuleNameIndex);

    return Stream.concat(Stream.concat(Stream.concat(coreIndices, javaIndices), xmlIndices), ktIndices);
  }
}
