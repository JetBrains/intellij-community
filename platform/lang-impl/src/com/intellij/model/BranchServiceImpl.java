// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

class BranchServiceImpl extends BranchService implements Disposable {

  BranchServiceImpl() {
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        Document document = event.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        if (file instanceof BranchedVirtualFile) {
          ((ModelBranchImpl)((BranchedVirtualFile)file).branch).registerDocumentChange(document, event);
        }
      }
    }, this);
  }

  @Override
  public void dispose() {}

  @Override
  @NotNull ModelPatch performInBranch(@NotNull Project project, @NotNull Consumer<ModelBranch> action) {
    return PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(
      () -> ModelBranchImpl.performInBranch(project, action));
  }
}