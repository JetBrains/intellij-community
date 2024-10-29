// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.*;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileSystemItemFilter;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @author gregsh
 */
public final class ScratchTreeStructureProvider implements TreeStructureProvider, DumbAware {

  public ScratchTreeStructureProvider(Project project) {
    registerUpdaters(project, project, new Runnable() {
      AbstractProjectViewPane updateTarget;
      @Override
      public void run() {
        if (project.isDisposed()) return;
        if (updateTarget == null) {
          updateTarget = ProjectView.getInstance(project).getProjectViewPaneById(ProjectViewPane.ID);
        }
        if (updateTarget != null) updateTarget.updateFromRoot(true);
      }
    });
  }

  private static void registerUpdaters(@NotNull Project project, @NotNull Disposable disposable, @NotNull Runnable onUpdate) {
    VirtualFileManager.getInstance().addAsyncFileListener(events -> {
      ScratchFileService scratchFileService = ScratchFileService.getInstance();
      boolean update = JBIterable.from(events).find(e -> {
        ProgressManager.checkCanceled();
        VirtualFile parent = getNewParent(e);
        if (parent == null) return false;
        if (ScratchUtil.isScratch(parent)) return true;
        if (!isDirectory(e)) return false;
        for (RootType rootType : RootType.getAllRootTypes()) {
          if (scratchFileService.getRootPath(rootType).startsWith(parent.getPath())) return true;
        }
        return false;
      }) != null;

      return !update ? null : new AsyncFileListener.ChangeApplier() {
        @Override
        public void afterVfsChange() {
          onUpdate.run();
        }
      };
    }, disposable);
    ConcurrentMap<RootType, Disposable> disposables = ConcurrentFactoryMap.createMap(o -> Disposer.newDisposable(o.getDisplayName()));
    for (RootType rootType : RootType.getAllRootTypes()) {
      registerRootTypeUpdater(project, rootType, onUpdate, disposable, disposables);
    }
    RootType.ROOT_EP.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull RootType rootType, @NotNull PluginDescriptor pluginDescriptor) {
        registerRootTypeUpdater(project, rootType, onUpdate, disposable, disposables);
      }

      @Override
      public void extensionRemoved(@NotNull RootType rootType, @NotNull PluginDescriptor pluginDescriptor) {
        Disposable rootDisposable = disposables.remove(rootType);
        if (rootDisposable != null) Disposer.dispose(rootDisposable);
      }
    }, project);
    RootType.ROOT_EP.addChangeListener(onUpdate, project);
  }

  private static void registerRootTypeUpdater(@NotNull Project project,
                                              @NotNull RootType rootType,
                                              @NotNull Runnable onUpdate,
                                              @NotNull Disposable parentDisposable,
                                              @NotNull Map<RootType, Disposable> disposables) {
    if (rootType.isHidden()) return;
    Disposable rootDisposable = disposables.get(rootType);
    Disposer.register(parentDisposable, rootDisposable);

    ReadAction.run(() -> {
      if (project.isDisposed()) return;

      rootType.registerTreeUpdater(project, parentDisposable, onUpdate);
    });
  }

  private static VirtualFile getNewParent(@NotNull VFileEvent e) {
    if (e instanceof VFileMoveEvent) {
      return ((VFileMoveEvent)e).getNewParent();
    }
    else if (e instanceof VFileCopyEvent) {
      return ((VFileCopyEvent)e).getNewParent();
    }
    else if (e instanceof VFileCreateEvent) {
      return ((VFileCreateEvent)e).getParent();
    }
    else {
      return Objects.requireNonNull(e.getFile()).getParent();
    }
  }

  private static boolean isDirectory(@NotNull VFileEvent e) {
    if (e instanceof VFileCreateEvent) {
      return ((VFileCreateEvent)e).isDirectory();
    }
    else {
      return Objects.requireNonNull(e.getFile()).isDirectory();
    }
  }

  private static @Nullable PsiDirectory getDirectory(@NotNull Project project, @NotNull RootType rootType) {
    VirtualFile virtualFile = getVirtualFile(rootType);
    return virtualFile == null ? null : PsiManager.getInstance(project).findDirectory(virtualFile);
  }

  public static @Nullable VirtualFile getVirtualFile(@NotNull RootType rootType) {
    String path = ScratchFileService.getInstance().getRootPath(rootType);
    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  private static @Nullable AbstractTreeNode<?> createRootTypeNode(@NotNull Project project, @NotNull RootType rootType, @NotNull ViewSettings settings) {
    if (rootType.isHidden()) return null;
    MyRootNode node = new MyRootNode(project, rootType, settings);
    return node.isEmpty() ? null : node;
  }

  @Override
  public @NotNull Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> parent,
                                                         @NotNull Collection<AbstractTreeNode<?>> children,
                                                         ViewSettings settings) {
    if (!settings.isShowScratchesAndConsoles()) return children;
    Project project = parent instanceof ProjectViewProjectNode ? parent.getProject() : null;
    if (project == null) return children;
    if (ApplicationManager.getApplication().isUnitTestMode()) return children;
    if (children.isEmpty() &&
        JBIterable.from(RootType.getAllRootTypes()).filterMap(o -> createRootTypeNode(project, o, settings)).isEmpty()) {
      return children;
    }
    List<AbstractTreeNode<?>> list = new ArrayList<>(children.size() + 1);
    list.addAll(children);
    list.add(new MyProjectNode(project, settings));
    return list;
  }

  /**
   * @deprecated Use modify method instead
   */
  @Deprecated(forRemoval = true)
  public static AbstractTreeNode<?> createRootNode(@NotNull Project project, ViewSettings settings) {
    return new MyProjectNode(project, settings);
  }

  static class DataRule implements UiDataRule {

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink, @NotNull DataSnapshot snapshot) {
      AbstractTreeNode<?> node = JBIterable.of(snapshot.get(PlatformCoreDataKeys.SELECTED_ITEMS))
        .filter(AbstractTreeNode.class)
        .single();
      if (node instanceof MyRootNode selection) {
        sink.lazy(LangDataKeys.PASTE_TARGET_PSI_ELEMENT, () -> {
          VirtualFile file = selection.getVirtualFile();
          Project project = selection.getProject();
          return file == null || project == null ? null : PsiManager.getInstance(project).findDirectory(file);
        });
      }
    }
  }

  private static final class MyProjectNode extends ProjectViewNode<String> {
    MyProjectNode(Project project, ViewSettings settings) {
      super(project, ScratchesNamedScope.scratchesAndConsoles(), settings);
    }

    @Override
    public boolean isIncludedInExpandAll() {
      return false;
    }

    @Override
    public @NotNull NodeSortOrder getSortOrder(@NotNull NodeSortSettings settings) {
      return NodeSortOrder.SCRATCH_ROOT;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return ScratchUtil.isScratch(file);
    }

    @Override
    public @NotNull Collection<? extends AbstractTreeNode<?>> getChildren() {
      List<AbstractTreeNode<?>> list = new ArrayList<>();
      Project project = Objects.requireNonNull(getProject());
      for (RootType rootType : RootType.getAllRootTypes()) {
        ContainerUtil.addIfNotNull(list, createRootTypeNode(project, rootType, getSettings()));
      }
      return list;
    }

    @Override
    protected void update(@NotNull PresentationData presentation) {
      presentation.setPresentableText(getValue());
      presentation.setIcon(AllIcons.Scope.Scratches);
    }

    @Override
    public boolean canRepresent(Object element) {
      return false;
    }
  }

  private static final class MyRootNode extends ProjectViewNode<RootType> implements PsiFileSystemItemFilter {
    MyRootNode(Project project, @NotNull RootType type, ViewSettings settings) {
      super(project, type, settings);
    }

    public @NotNull RootType getRootType() {
      return Objects.requireNonNull(getValue());
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return getValue().containsFile(file);
    }

    @Override
    public @Nullable VirtualFile getVirtualFile() {
      return ScratchTreeStructureProvider.getVirtualFile(getRootType());
    }

    @Override
    protected @NotNull String getCacheableFilePath() {
      return ScratchFileService.getInstance().getRootPath(getRootType());
    }

    @Override
    public @NotNull Collection<VirtualFile> getRoots() {
      return getDefaultRootsFor(getVirtualFile());
    }

    @Override
    public @NotNull Collection<? extends AbstractTreeNode<?>> getChildren() {
      //noinspection ConstantConditions
      return getDirectoryChildrenImpl(getProject(), getDirectory(), getSettings(), this);
    }

    PsiDirectory getDirectory() {
      //noinspection ConstantConditions
      return ScratchTreeStructureProvider.getDirectory(getProject(), getValue());
    }

    @Override
    protected void update(@NotNull PresentationData presentation) {
      presentation.setIcon(AllIcons.Nodes.Folder);
      presentation.setPresentableText(getRootType().getDisplayName());
    }

    @Override
    public boolean canRepresent(Object element) {
      return Comparing.equal(getDirectory(), element);
    }

    public boolean isEmpty() {
      VirtualFile root = getVirtualFile();
      if (root == null) return true;
      RootType rootType = getRootType();
      Project project = Objects.requireNonNull(getProject());
      for (VirtualFile f : root.getChildren()) {
        if (!rootType.isIgnored(project, f)) return false;
      }
      return true;
    }

    @Override
    public boolean shouldShow(@NotNull PsiFileSystemItem item) {
      //noinspection ConstantConditions
      return !getRootType().isIgnored(getProject(), item.getVirtualFile());
    }

    static @NotNull Collection<AbstractTreeNode<?>> getDirectoryChildrenImpl(@NotNull Project project,
                                                                             @Nullable PsiDirectory directory,
                                                                             @NotNull ViewSettings settings,
                                                                             @NotNull PsiFileSystemItemFilter filter) {
      final List<AbstractTreeNode<?>> result = new ArrayList<>();
      PsiElementProcessor<PsiFileSystemItem> processor = new PsiElementProcessor<>() {
        @Override
        public boolean execute(@NotNull PsiFileSystemItem element) {
          if (!filter.shouldShow(element)) {
            // skip
          }
          else if (element instanceof PsiDirectory) {
            result.add(new PsiDirectoryNode(project, (PsiDirectory)element, settings, filter) {
              @Override
              public Collection<AbstractTreeNode<?>> getChildrenImpl() {
                //noinspection ConstantConditions
                return getDirectoryChildrenImpl(getProject(), getValue(), getSettings(), getFilter());
              }
            });
          }
          else if (element instanceof PsiFile) {
            result.add(new PsiFileNode(project, (PsiFile)element, settings) {
              @Override
              public Comparable<ExtensionSortKey> getTypeSortKey() {
                PsiFile value = getValue();
                Language language = value == null ? null : value.getLanguage();
                LanguageFileType fileType = language == null ? null : language.getAssociatedFileType();
                return fileType == null ? null : new ExtensionSortKey(fileType.getDefaultExtension());
              }
            });
          }
          return true;
        }
      };
      if (directory == null || !directory.isValid()) return Collections.emptyList();
      directory.processChildren(processor);
      return result;
    }
  }
}
