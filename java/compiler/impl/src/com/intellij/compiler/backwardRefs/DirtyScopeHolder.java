// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.backwardRefs.view.DirtyScopeTestInfo;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludedEntriesListener;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener;
import com.intellij.platform.backend.workspace.WorkspaceModelTopics;
import com.intellij.platform.workspace.jps.entities.ContentRootEntity;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.storage.EntityChange;
import com.intellij.platform.workspace.storage.VersionedStorageChange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleEntityUtils;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.BiConsumer;

public final class DirtyScopeHolder extends UserDataHolderBase implements AsyncFileListener {
  private final Project myProject;
  private final Set<FileType> myFileTypes;
  private final ProjectFileIndex myProjectFileIndex;
  private final ModificationTracker myModificationTracker;
  private final Object myLock = new Object();

  private final Set<Module> myVFSChangedModules = new HashSet<>(); // guarded by myLock

  private final Set<Module> myChangedModulesDuringCompilation = new HashSet<>(); // guarded by myLock

  private final List<ExcludeEntryDescription> myExcludedDescriptions = new SmartList<>(); // guarded by myLock
  private boolean myCompilationPhase; // guarded by myLock
  private volatile GlobalSearchScope myExcludedFilesScope; // calculated outside myLock
  private final Set<String> myCompilationAffectedModules = ConcurrentCollectionFactory.createConcurrentSet(); // used outside myLock

  private final FileTypeRegistry myFileTypeRegistry = FileTypeRegistry.getInstance();

  public DirtyScopeHolder(@NotNull Project project,
                          @NotNull Set<FileType> fileTypes,
                          @NotNull ProjectFileIndex projectFileIndex,
                          @NotNull Disposable parentDisposable,
                          @NotNull ModificationTracker modificationTracker,
                          @NotNull BiConsumer<? super MessageBusConnection, ? super Set<String>> compilationAffectedModulesSubscription) {
    myProject = project;
    myFileTypes = fileTypes;
    myProjectFileIndex = projectFileIndex;
    myModificationTracker = modificationTracker;

    if (!CompilerReferenceServiceBase.isEnabled()) {
      return;
    }

    MessageBusConnection connect = project.getMessageBus().connect(parentDisposable);
    connect.subscribe(ExcludedEntriesListener.TOPIC, new ExcludedEntriesListener() {
      @Override
      public void onEntryAdded(@NotNull ExcludeEntryDescription description) {
        synchronized (myLock) {
          if (myCompilationPhase) {
            myExcludedDescriptions.add(description);
          }
        }
      }
    });

    compilationAffectedModulesSubscription.accept(connect, myCompilationAffectedModules);

    connect.subscribe(WorkspaceModelTopics.CHANGED, new WorkspaceModelChangeListener() {
      @Override
      public void changed(@NotNull VersionedStorageChange event) {
        for (EntityChange<ModuleEntity> change : event.getChanges(ModuleEntity.class)) {
          ModuleEntity newEntity = change.getNewEntity();
          if (newEntity != null) {
            addToDirtyModules(ModuleEntityUtils.findModule(newEntity, event.getStorageAfter()));
          }
          ModuleEntity oldEntity = change.getOldEntity();
          if (oldEntity != null) {
            addToDirtyModules(ModuleEntityUtils.findModule(oldEntity, event.getStorageBefore()));
          }
        }
        for (EntityChange<ContentRootEntity> change : event.getChanges(ContentRootEntity.class)) {
          ContentRootEntity newEntity = change.getNewEntity();
          if (newEntity != null) {
            addToDirtyModules(ModuleEntityUtils.findModule(newEntity.getModule(), event.getStorageAfter()));
          }
          ContentRootEntity oldEntity = change.getOldEntity();
          if (oldEntity != null) {
            addToDirtyModules(ModuleEntityUtils.findModule(oldEntity.getModule(), event.getStorageBefore()));
          }
        }
        clearDisposedModules();
      }
    });
  }

  public void compilerActivityStarted() {
    final ExcludeEntryDescription[] excludeEntryDescriptions =
      CompilerConfiguration.getInstance(myProject).getExcludedEntriesConfiguration().getExcludeEntryDescriptions();
    synchronized (myLock) {
      myCompilationPhase = true;
      Collections.addAll(myExcludedDescriptions, excludeEntryDescriptions);
      myExcludedFilesScope = null;
      myCompilationAffectedModules.clear();
    }
  }

  public void upToDateCheckFinished(@Nullable Collection<@NotNull Module> allModules, @Nullable Collection<@NotNull Module> compiledModules) {
    compilationFinished(() -> {
      if (allModules != null) myVFSChangedModules.addAll(allModules);
      if (compiledModules != null) compiledModules.forEach(myVFSChangedModules::remove);
    });
  }

  public @NotNull Set<String> getCompilationAffectedModules() {
    return myCompilationAffectedModules;
  }

  public void compilerActivityFinished(List<Module> compiledModules) {
    compilationFinished(() -> {
      if (compiledModules == null) return;
      compiledModules.forEach(myVFSChangedModules::remove);
    });
  }

  private void compilationFinished(@NotNull Runnable action) {
    ExcludeEntryDescription[] descriptions;
    synchronized (myLock) {
      myCompilationPhase = false;
      action.run();
      myVFSChangedModules.addAll(myChangedModulesDuringCompilation);
      myChangedModulesDuringCompilation.clear();
      descriptions = myExcludedDescriptions.toArray(new ExcludeEntryDescription[0]);
      myExcludedDescriptions.clear();
    }
    myCompilationAffectedModules.clear();
    myExcludedFilesScope = ExcludedFromCompileFilesUtil.getExcludedFilesScope(descriptions, myFileTypes, myProject);
  }

  public @NotNull GlobalSearchScope getDirtyScope() {
    final Project project = myProject;
    return ReadAction.compute(() -> {
      synchronized (myLock) {
        if (myCompilationPhase) {
          return ProjectScope.getContentScope(project);
        }
        if (project.isDisposed()) throw new ProcessCanceledException();
        return CachedValuesManager.getManager(project).getCachedValue(this, () ->
          CachedValueProvider.Result
            .create(calculateDirtyScope(), PsiModificationTracker.MODIFICATION_COUNT, VirtualFileManager.getInstance(),
                    myModificationTracker));
      }
    });
  }

  private @NotNull GlobalSearchScope calculateDirtyScope() {
    final Set<Module> dirtyModules = getAllDirtyModules();
    if (dirtyModules.isEmpty()) return myExcludedFilesScope;
    if (dirtyModules.size() == ModuleManager.getInstance(myProject).getModules().length) {
      return ProjectScope.getContentScope(myProject);
    }
    return GlobalSearchScope.union(
      ContainerUtil.append(ContainerUtil.map(dirtyModules, Module::getModuleWithDependentsScope), myExcludedFilesScope));
  }

  @ApiStatus.Internal
  @VisibleForTesting
  public @NotNull Set<Module> getAllDirtyModules() {
    Set<Module> dirtyModules;
    synchronized (myLock) {
      dirtyModules = new HashSet<>(myVFSChangedModules);
    }

    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    for (Document document : fileDocumentManager.getUnsavedDocuments()) {
      VirtualFile file = fileDocumentManager.getFile(document);
      if (file == null) {
        continue;
      }
      Module m = getModuleForSourceContentFile(file);
      if (m != null) {
        dirtyModules.add(m);
      }
    }

    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myProject);
    for (Document document : psiDocumentManager.getUncommittedDocuments()) {
      final PsiFile psiFile = psiDocumentManager.getPsiFile(document);
      if (psiFile == null) continue;
      final VirtualFile file = psiFile.getVirtualFile();
      if (file == null) continue;
      final Module m = getModuleForSourceContentFile(file);
      if (m != null) dirtyModules.add(m);
    }
    return dirtyModules;
  }

  public boolean contains(@NotNull VirtualFile file) {
    return getDirtyScope().contains(file);
  }

  @Override
  public @Nullable ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
    if (myProject.isDisposed()) return null;
    List<Module> modulesToBeMarkedDirty = getModulesToBeMarkedDirtyBefore(events);

    return new ChangeApplier() {
      @Override
      public void beforeVfsChange() {
        modulesToBeMarkedDirty.forEach(DirtyScopeHolder.this::addToDirtyModules);
      }

      @Override
      public void afterVfsChange() {
        if (!myProject.isDisposed()) {
          after(events);
        }
      }
    };
  }

  private void after(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      if (event instanceof VFileCreateEvent) {
        VirtualFile parent = ((VFileCreateEvent)event).getParent();
        String fileName = ((VFileCreateEvent)event).getChildName();
        Module module = getModuleForSourceContentFile(parent, fileName);
        if (module != null) {
          addToDirtyModules(module);
        }
      }
      else if (event instanceof VFileCopyEvent || event instanceof VFileMoveEvent) {
        VirtualFile file = event.getFile();
        assert file != null;
        fileChanged(file);
      }
      else {
        if (event instanceof VFilePropertyChangeEvent pce) {
          String propertyName = pce.getPropertyName();
          if (VirtualFile.PROP_NAME.equals(propertyName) || VirtualFile.PROP_SYMLINK_TARGET.equals(propertyName)) {
            fileChanged(pce.getFile());
          }
        }
      }
    }
  }

  @Contract(pure = true)
  private @NotNull List<Module> getModulesToBeMarkedDirtyBefore(@NotNull List<? extends VFileEvent> events) {
    final List<Module> modulesToBeMarkedDirty = new ArrayList<>();

    for (VFileEvent event : events) {
      ProgressManager.checkCanceled();

      if (event instanceof VFileDeleteEvent || event instanceof VFileMoveEvent || event instanceof VFileContentChangeEvent) {
        VirtualFile file = event.getFile();
        assert file != null;
        final Module module = getModuleForSourceContentFile(file);
        ContainerUtil.addIfNotNull(modulesToBeMarkedDirty, module);
      }
      else if (event instanceof VFilePropertyChangeEvent pce) {
        String propertyName = pce.getPropertyName();
        if (VirtualFile.PROP_NAME.equals(propertyName) || VirtualFile.PROP_SYMLINK_TARGET.equals(propertyName)) {
          final String path = pce.getFile().getPath();
          for (Module module : ModuleManager.getInstance(myProject).getModules()) {
            if (FileUtil.isAncestor(path, module.getModuleFilePath(), true)) {
              modulesToBeMarkedDirty.add(module);
            }
          }
        }
      }
    }
    return modulesToBeMarkedDirty;
  }

  public void installVFSListener(@NotNull Disposable parentDisposable) {
    VirtualFileManager.getInstance().addAsyncFileListener(this, parentDisposable);
  }

  private void fileChanged(@NotNull VirtualFile file) {
    final Module module = getModuleForSourceContentFile(file);
    if (module != null) {
      addToDirtyModules(module);
    }
  }

  private void addToDirtyModules(@Nullable Module module) {
    if (module == null) return;
    synchronized (myLock) {
      if (myCompilationPhase) {
        myChangedModulesDuringCompilation.add(module);
      }
      else {
        myVFSChangedModules.add(module);
      }
    }
  }

  private void clearDisposedModules() {
    synchronized (myLock) {
      myChangedModulesDuringCompilation.removeIf(module -> module.isDisposed());
      myVFSChangedModules.removeIf(module -> module.isDisposed());
    }
  }

  private Module getModuleForSourceContentFile(@NotNull VirtualFile file) {
    return getModuleForSourceContentFile(file, file.getNameSequence());
  }

  private Module getModuleForSourceContentFile(@NotNull VirtualFile parent, @NotNull CharSequence fileName) {
    FileType fileType = myFileTypeRegistry.getFileTypeByFileName(fileName);
    if (myFileTypes.contains(fileType) && myProjectFileIndex.isInSourceContent(parent)) {
      return myProjectFileIndex.getModuleForFile(parent);
    }
    return null;
  }

  public @NotNull DirtyScopeTestInfo getState() {
    synchronized (myLock) {
      final Module[] vfsChangedModules = myVFSChangedModules.toArray(Module.EMPTY_ARRAY);
      final List<Module> unsavedChangedModuleList = new ArrayList<>(getAllDirtyModules());
      ContainerUtil.removeAll(unsavedChangedModuleList, vfsChangedModules);
      final Module[] unsavedChangedModules = unsavedChangedModuleList.toArray(Module.EMPTY_ARRAY);
      //noinspection unchecked
      final List<VirtualFile> excludedFiles = myExcludedFilesScope instanceof Iterable
                                              ? ContainerUtil.newArrayList((Iterable<VirtualFile>)myExcludedFilesScope)
                                              : Collections.emptyList();
      return new DirtyScopeTestInfo(vfsChangedModules, unsavedChangedModules, excludedFiles.toArray(VirtualFile.EMPTY_ARRAY),
                                    getDirtyScope());
    }
  }
}
