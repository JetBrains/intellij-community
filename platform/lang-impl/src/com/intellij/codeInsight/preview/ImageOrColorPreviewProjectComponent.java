/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.preview;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class ImageOrColorPreviewProjectComponent extends AbstractProjectComponent {

  public ImageOrColorPreviewProjectComponent(final Project project) {
    super(project);
  }

  public void projectOpened() {
    FileEditorManager.getInstance(myProject).addFileEditorManagerListener(new MyFileEditorManagerListener(), myProject);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ImageOrColorPreviewComponent";
  }

  private static class MyFileEditorManagerListener extends FileEditorManagerAdapter {
    public void fileOpened(final FileEditorManager source, final VirtualFile file) {
      if (isSuitable(source.getProject(), file)) {
        final FileEditor[] fileEditors = source.getEditors(file);
        for (final FileEditor each : fileEditors) {
          if (each instanceof TextEditor) {
            Disposer.register(each, new ImageOrColorPreviewManager((TextEditor)each));
          }
        }
      }
    }

    private static boolean isSuitable(final Project project, final VirtualFile file) {
      final FileViewProvider provider = PsiManager.getInstance(project).findViewProvider(file);
      if (provider == null) return false;
      
      for (final PsiFile psiFile : provider.getAllFiles()) {
        for(PreviewHintProvider hintProvider: Extensions.getExtensions(PreviewHintProvider.EP_NAME)) {
          if (hintProvider.isSupportedFile(psiFile)) {
            return true;
          }
        }
      }

      return false;
    }
  }


}
