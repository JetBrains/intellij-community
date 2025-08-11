// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.java.workspace.entities.JavaProjectSettingsEntity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ProjectExtension;
import com.intellij.openapi.roots.WatchedRootsProvider;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.LightFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.platform.backend.workspace.VirtualFileUrls;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import kotlin.Unit;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

final class CompilerProjectExtensionImpl extends CompilerProjectExtension implements Disposable {
  private static final String OUTPUT_TAG = "output";
  private static final String URL = "url";
  private static final Logger LOG = Logger.getInstance(CompilerProjectExtensionImpl.class);

  // This field is not used when useWsm set to `true`
  private VirtualFilePointer myCompilerOutput;

  private LocalFileSystem.WatchRequest myCompilerOutputWatchRequest;
  private final Project project;
  private final boolean useWsm = Registry.is("project.root.manager.over.wsm", true);

  CompilerProjectExtensionImpl(@NotNull Project project) {
    this.project = project;
  }

  /**
   * Returns true if the compiler output was changed after read
   */
  private boolean readExternal(@NotNull Element element) {
    Element pathElement = element.getChild(OUTPUT_TAG);
    if (pathElement != null) {
      String outputPath = pathElement.getAttributeValue(URL);
      VirtualFilePointer oldValue = myCompilerOutput;
      myCompilerOutput = outputPath != null ? VirtualFilePointerManager.getInstance().create(outputPath, this, null) : null;

      return !Objects.equals(
        oldValue != null ? oldValue.getUrl() : null,
        myCompilerOutput != null ? myCompilerOutput.getUrl() : null
      );
    }
    return false;
  }

  private void writeExternal(@NotNull Element element) {
    if (myCompilerOutput != null) {
      Element pathElement = new Element(OUTPUT_TAG);
      pathElement.setAttribute(URL, myCompilerOutput.getUrl());
      element.addContent(pathElement);
    }
  }

  @Override
  public void dispose() {
    myCompilerOutput = null;
  }

  private @Nullable VirtualFileUrl getCompilerOutputWSM() {
    LOG.assertTrue(useWsm, "This code does should not be used when project.root.manager.over.wsm is false");
    JavaProjectSettingsEntity entity = JavaEntitiesWsmUtils.getSingleEntity(WorkspaceModel.getInstance(project).getCurrentSnapshot(), JavaProjectSettingsEntity.class);
    return entity != null ? entity.getCompilerOutput() : null;
  }

  @RequiresWriteLock
  private void setCompilerOutputWSM(@Nullable String fileUrl) {
    LOG.assertTrue(useWsm, "This code does should not be used when project.root.manager.over.wsm is false");
    ThreadingAssertions.assertWriteAccess();

    WorkspaceModel workspaceModel = WorkspaceModel.getInstance(project);
    VirtualFileUrlManager vfum = workspaceModel.getVirtualFileUrlManager();
    workspaceModel.updateProjectModel("setCompilerOutputWSM", mutableStorage -> {
      JavaEntitiesWsmUtils.addOrModifyJavaProjectSettingsEntity(project, mutableStorage, entity -> {
        VirtualFileUrl vfu = fileUrl != null ? vfum.getOrCreateFromUrl(fileUrl) : null;
        entity.setCompilerOutput(vfu);
      });
      return Unit.INSTANCE;
    });
  }

  @Override
  public VirtualFile getCompilerOutput() {
    if (useWsm) {
      VirtualFileUrl fileUrl = getCompilerOutputWSM();
      return fileUrl != null ? VirtualFileUrls.getVirtualFile(fileUrl) : null;
    }
    else {
      return myCompilerOutput != null ? myCompilerOutput.getFile() : null;
    }
  }

  @Override
  public String getCompilerOutputUrl() {
    if (useWsm) {
      VirtualFileUrl fileUrl = getCompilerOutputWSM();
      return fileUrl != null ? fileUrl.getUrl() : null;
    }
    else {
      return myCompilerOutput != null ? myCompilerOutput.getUrl() : null;
    }
  }

  @Override
  public @Nullable VirtualFilePointer getCompilerOutputPointer() {
    if (useWsm) {
      VirtualFileUrl fileUrl = getCompilerOutputWSM();
      if (fileUrl == null) {
        return null;
      }
      else if (fileUrl instanceof VirtualFilePointer virtualFilePointer) {
        return virtualFilePointer;
      }
      else {
        return new LightFilePointer(fileUrl.getUrl());
      }
    }
    else {
      return myCompilerOutput;
    }
  }

  @Override
  @RequiresWriteLock(generateAssertion = false)
  public void setCompilerOutputPointer(@Nullable VirtualFilePointer pointer) {
    LOG.assertTrue(ApplicationManager.getApplication().isWriteAccessAllowed(),
                   "Compiler outputs may only be updated under write action. " +
                   "This method is deprecated. Please consider using `setCompilerOutputUrl` instead.");

    if (useWsm) {
      setCompilerOutputWSM(pointer != null ? pointer.getUrl() : null);
    }
    else {
      myCompilerOutput = pointer;
    }
  }

  @Override
  @RequiresWriteLock(generateAssertion = false)
  public void setCompilerOutputUrl(@Nullable String compilerOutputUrl) {
    LOG.assertTrue(ApplicationManager.getApplication().isWriteAccessAllowed(),
                   "Compiler outputs may only be updated under write action. " +
                   "Please acquire write action before invoking setCompilerOutputUrl.");

    if (compilerOutputUrl == null) {
      setCompilerOutputPointer(null);
    }
    else {
      // TODO ANK (Maybe): maybe we should remove old compilerOutputUrl from watched roots? (keep in mind that there might be
      //  some other code which has added exactly the same root to the watch roots)
      if (useWsm) {
        setCompilerOutputWSM(compilerOutputUrl);
      }
      else {
        VirtualFilePointer pointer = VirtualFilePointerManager.getInstance().create(compilerOutputUrl, this, null);
        setCompilerOutputPointer(pointer);
      }
      String path = VfsUtilCore.urlToPath(compilerOutputUrl);
      myCompilerOutputWatchRequest = LocalFileSystem.getInstance().replaceWatchedRoot(myCompilerOutputWatchRequest, path, true);
    }
  }

  private static Set<String> getRootsToWatch(Project project) {
    Set<String> rootsToWatch = new HashSet<>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
      if (extension != null && !extension.isCompilerOutputPathInherited()) {
        String outputUrl = extension.getCompilerOutputUrl();
        if (outputUrl != null && outputUrl.startsWith(StandardFileSystems.FILE_PROTOCOL_PREFIX)) {
          rootsToWatch.add(ProjectRootManagerImpl.Companion.extractLocalPath(outputUrl));
        }
        String testOutputUrl = extension.getCompilerOutputUrlForTests();
        if (testOutputUrl!= null && testOutputUrl.startsWith(StandardFileSystems.FILE_PROTOCOL_PREFIX)) {
          rootsToWatch.add(ProjectRootManagerImpl.Companion.extractLocalPath(testOutputUrl));
        }
      }
      // otherwise, the module output path is beneath the CompilerProjectExtension.getCompilerOutputUrl() which is added below
    }

    CompilerProjectExtension extension = CompilerProjectExtension.getInstance(project);
    if (extension != null) {
      String compilerOutputUrl = extension.getCompilerOutputUrl();
      if (compilerOutputUrl != null && compilerOutputUrl.startsWith(StandardFileSystems.FILE_PROTOCOL_PREFIX)) {
        rootsToWatch.add(ProjectRootManagerImpl.Companion.extractLocalPath(compilerOutputUrl));
      }
    }

    return rootsToWatch;
  }

  private static CompilerProjectExtensionImpl getImpl(Project project) {
    return (CompilerProjectExtensionImpl)CompilerProjectExtension.getInstance(project);
  }

  static final class MyProjectExtension extends ProjectExtension {
    private final Project myProject;

    MyProjectExtension(Project project) {
      myProject = project;
    }

    @Override
    public boolean readExternalElement(@NotNull Element element) {
      return getImpl(myProject).readExternal(element);
    }

    @Override
    public void writeExternal(@NotNull Element element) {
      getImpl(myProject).writeExternal(element);
    }
  }

  static final class MyWatchedRootsProvider implements WatchedRootsProvider {
    @Override
    public @NotNull Set<String> getRootsToWatch(@NotNull Project project) {
      return CompilerProjectExtensionImpl.getRootsToWatch(project);
    }
  }
}
