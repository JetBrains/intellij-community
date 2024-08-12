// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.storage.ImmutableEntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ExternalLibrariesNode extends ProjectViewNode<String> {
  private static final Logger LOG = Logger.getInstance(ExternalLibrariesNode.class);

  public ExternalLibrariesNode(@NotNull Project project, ViewSettings viewSettings) {
    super(project, "External Libraries", viewSettings);
  }

  @Override
  public boolean isAlwaysShowPlus() {
    return true;
  }

  @Override
  public boolean isIncludedInExpandAll() {
    return false;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    Project project = Objects.requireNonNull(getProject());
    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    return index.isInLibrary(file) && someChildContainsFile(file, false);
  }

  @Override
  public @NotNull Collection<? extends AbstractTreeNode<?>> getChildren() {
    Project project = Objects.requireNonNull(getProject());
    List<AbstractTreeNode<?>> children = new ArrayList<>();
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
    Module[] modules = ModuleManager.getInstance(project).getModules();
    Map<String, List<Library>> processedLibraries = new HashMap<>();
    Set<Sdk> processedSdk = new HashSet<>();

    for (Module module : modules) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      for (final OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof LibraryOrderEntry libraryOrderEntry) {
          final Library library = libraryOrderEntry.getLibrary();
          if (library == null) continue;
          String libraryPresentableName = libraryOrderEntry.getPresentableName();
          List<Library> librariesWithSameName = processedLibraries.getOrDefault(libraryPresentableName, new ArrayList<>());
          if (ContainerUtil.exists(librariesWithSameName, processedLibrary -> processedLibrary.hasSameContent(library))) continue;
          librariesWithSameName.add(library);
          processedLibraries.put(libraryPresentableName, librariesWithSameName);

          if (!hasExternalEntries(fileIndex, libraryOrderEntry)) continue;

          final String libraryName = library.getName();
          if (libraryName == null || libraryName.isEmpty()) {
            addLibraryChildren(libraryOrderEntry, children, project, this);
          }
          else {
            children.add(new NamedLibraryElementNode(project, new NamedLibraryElement(null, libraryOrderEntry), getSettings()));
          }
        }
        else if (orderEntry instanceof JdkOrderEntry jdkOrderEntry) {
          final Sdk jdk = jdkOrderEntry.getJdk();
          if (jdk != null) {
            if (processedSdk.contains(jdk)) continue;
            processedSdk.add(jdk);
            children.add(new NamedLibraryElementNode(project, new NamedLibraryElement(null, jdkOrderEntry), getSettings()));
          }
        }
      }
    }
    for (AdditionalLibraryRootsProvider provider : AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
      Collection<SyntheticLibrary> libraries = provider.getAdditionalProjectLibraries(project);
      for (SyntheticLibrary library : libraries) {
        if (library.isShowInExternalLibrariesNode()) {
          if (!(library instanceof ItemPresentation)) {
            LOG.warn("Synthetic library must implement ItemPresentation to be shown in External Libraries node: "
                     + libraries.getClass().getSimpleName());
            continue;
          }
          children.add(new SyntheticLibraryElementNode(project, library, (ItemPresentation)library, getSettings()));
        }
      }
    }
    List<ExternalLibrariesWorkspaceModelNodesProvider<?>> extensionList =
      ExternalLibrariesWorkspaceModelNodesProvider.EP.getExtensionList();
    if (!extensionList.isEmpty()) {
      ImmutableEntityStorage current = WorkspaceModel.getInstance(project).getCurrentSnapshot();
      for (ExternalLibrariesWorkspaceModelNodesProvider<?> provider : extensionList) {
        handleProvider(provider, project, current, children);
      }
    }
    return children;
  }

  private <T extends WorkspaceEntity> void handleProvider(ExternalLibrariesWorkspaceModelNodesProvider<T> provider,
                                                          @NotNull Project project,
                                                          ImmutableEntityStorage storage,
                                                          List<? super AbstractTreeNode<?>> children) {
    Sequence<T> sequence = storage.entities(provider.getWorkspaceClass());
    for (T entity : SequencesKt.asIterable(sequence)) {
      ProgressManager.checkCanceled();
      AbstractTreeNode<?> node = provider.createNode(entity, project, getSettings());
      if (node != null) {
        children.add(node);
      }
    }
  }


  public static void addLibraryChildren(final LibraryOrderEntry entry,
                                        final List<? super AbstractTreeNode<?>> children,
                                        Project project,
                                        ProjectViewNode<?> node) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final VirtualFile[] files = entry.getRootFiles(OrderRootType.CLASSES);
    for (final VirtualFile file : files) {
      final PsiDirectory psiDir = psiManager.findDirectory(file);
      if (psiDir == null) {
        continue;
      }
      children.add(new PsiDirectoryNode(project, psiDir, node.getSettings()));
    }
  }

  private static boolean hasExternalEntries(ProjectFileIndex index, LibraryOrderEntry orderEntry) {
    for (VirtualFile file : LibraryGroupNode.getLibraryRoots(orderEntry)) {
      if (!index.isInContent(VfsUtil.getLocalFile(file))) return true;
    }
    return false;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setPresentableText(IdeBundle.message("node.projectview.external.libraries"));
    presentation.setIcon(PlatformIcons.LIBRARY_ICON);
  }

  @Override
  public @NotNull NodeSortOrder getSortOrder(@NotNull NodeSortSettings settings) {
    return NodeSortOrder.LIBRARY_ROOT;
  }
}