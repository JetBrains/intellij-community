// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.ide.projectView.actions.MarkRootActionBase;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import static com.intellij.openapi.vfs.VfsUtilCore.pathToUrl;

public class AddSourceFolderListener implements VirtualFileListener {
  private final ContentRootData.SourceRoot myRoot;
  private final Project myProject;
  private final Module myModule;
  private final JpsModuleSourceRootType<?> mySourceRootType;

  public AddSourceFolderListener(ContentRootData.SourceRoot root,
                                 Module module,
                                 JpsModuleSourceRootType<?> sourceRootType) {
    myRoot = root;
    myProject = module.getProject();
    myModule = module;
    mySourceRootType = sourceRootType;
  }

  @Override
  public void fileCreated(@NotNull VirtualFileEvent event) {
    if (VfsUtilCore.isEqualOrAncestor(event.getFile().getUrl(), pathToUrl(myRoot.getPath()))) {

      final Ref<VirtualFile> ref = Ref.create();
      ExternalSystemApiUtil.doWriteAction(() -> ref.set(LocalFileSystem.getInstance().refreshAndFindFileByPath(myRoot.getPath())));
      final VirtualFile sourceFolderFile = ref.get();

      if (sourceFolderFile != null && sourceFolderFile.isValid()) {
        ExternalSystemApiUtil.executeProjectChangeAction(false, new DisposeAwareProjectChange(myProject) {
          @Override
          public void execute() {
            final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
            final ContentEntry entry = MarkRootActionBase.findContentEntry(rootModel, event.getFile());
            if (entry != null) {
              SourceFolder sourceFolder = entry.addSourceFolder(pathToUrl(myRoot.getPath()), mySourceRootType);
              if (!StringUtil.isEmpty(myRoot.getPackagePrefix())) {
                sourceFolder.setPackagePrefix(myRoot.getPackagePrefix());
              }
            }
            rootModel.commit();
          }
        });
      }
    }
  }
}
