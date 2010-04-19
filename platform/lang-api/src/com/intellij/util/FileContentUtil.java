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
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * @author peter
 */
public class FileContentUtil {
  public static final String FORCE_RELOAD_REQUESTOR = "FileContentUtil.saveOrReload";

  private FileContentUtil() {
  }

  public static void setFileText(@Nullable Project project, final VirtualFile virtualFile, final String text) throws IOException {
    if (project == null) {
      project = ProjectUtil.guessProjectForFile(virtualFile);
    }
    if (project != null) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
      final Document document = psiFile == null? null : psiDocumentManager.getDocument(psiFile);
      if (document != null) {
        document.setText(text != null ? text : "");
        psiDocumentManager.commitDocument(document);
        FileDocumentManager.getInstance().saveDocument(document);
        return;
      }
    }
    VfsUtil.saveText(virtualFile, text != null ? text : "");
    virtualFile.refresh(false, false);
  }

  public static void reparseFiles(@NotNull final Project project, @NotNull final Collection<VirtualFile> files, boolean includeOpenFiles) {
    final Set<VFilePropertyChangeEvent> list = new THashSet<VFilePropertyChangeEvent>();
    for (VirtualFile file : files) {
      saveOrReload(file, list);
    }
    if (includeOpenFiles) {
      for (VirtualFile open : FileEditorManager.getInstance(project).getOpenFiles()) {
        if (!files.contains(open)) {
          saveOrReload(open, list);
        }
      }
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().getMessageBus().syncPublisher(VirtualFileManager.VFS_CHANGES)
            .before(new ArrayList<VFileEvent>(list));
        ApplicationManager.getApplication().getMessageBus().syncPublisher(VirtualFileManager.VFS_CHANGES)
            .after(new ArrayList<VFileEvent>(list));
      }
    });
  }

  private static void saveOrReload(final VirtualFile virtualFile, Collection<VFilePropertyChangeEvent> events) {
    if (virtualFile == null || virtualFile.isDirectory()) {
      return;
    }
    final FileDocumentManager documentManager = FileDocumentManager.getInstance();
    if (documentManager.isFileModified(virtualFile)) {
      Document document = documentManager.getDocument(virtualFile);
      if (document != null) {
        documentManager.saveDocument(document);
      }
    }
    events.add(new VFilePropertyChangeEvent(FORCE_RELOAD_REQUESTOR, virtualFile, VirtualFile.PROP_NAME, virtualFile.getName(), virtualFile.getName(), false));
  }
}
