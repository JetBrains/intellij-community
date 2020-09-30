// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

class BranchServiceImpl extends BranchService implements Disposable {
  private static final Logger LOG = Logger.getInstance(BranchServiceImpl.class);

  BranchServiceImpl() {
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        Document document = event.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        if (file instanceof BranchedVirtualFileImpl) {
          BranchedVirtualFileImpl branchedFile = (BranchedVirtualFileImpl)file;
          branchedFile.getBranch().registerDocumentChange(document, event, branchedFile);
        }
      }
    }, this);
  }

  @Override
  public void dispose() {}

  @Override
  @NotNull ModelPatch performInBranch(@NotNull Project project, @NotNull Consumer<? super ModelBranch> action) {
    return PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(
      () -> ModelBranchImpl.performInBranch(action, new ModelBranchImpl(project) {
        @Override
        protected void assertAllChildrenLoaded(@NotNull VirtualFile file) {
          if (file instanceof VirtualDirectoryImpl) {
            LOG.assertTrue(((VirtualDirectoryImpl)file).allChildrenLoaded(),
                           "Partially loaded VFS children are not supported by model branches yet");
          }
        }
      }));
  }
}