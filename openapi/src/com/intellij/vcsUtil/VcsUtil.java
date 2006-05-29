/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.vcsUtil;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LocalVcsBundle;
import com.intellij.openapi.localVcs.LvcsObject;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class VcsUtil {

  public static void markFileAsUpToDate(VirtualFile file, Project project) {
    markAsUpToDate(project, file.isDirectory(), file.getPath());
    FileStatusManager.getInstance(project).fileStatusChanged(file);
    VcsDirtyScopeManager.getInstance(project).fileDirty(file);
  }

  private static void markAsUpToDate(Project project, boolean directory, String path) {
    LocalVcs localVcs = LocalVcs.getInstance(project);
    LvcsObject lvcsObject;
    if (directory) {
      lvcsObject = localVcs.findDirectory(path, true);
    }
    else {
      lvcsObject = localVcs.findFile(path, true);
    }
    if (lvcsObject != null) {
      lvcsObject.getRevision().setUpToDate(true);
    }
  }

  public static void markFileAsUpToDate(String path, Project project) {
    String canonicalPath = getCanonicalPath(new File(path));
    if (canonicalPath == null) return;
    markAsUpToDate(project, false, canonicalPath);
  }

  public static void markDirectoryAsUpToDate(String path, Project project) {
    String canonicalPath = getCanonicalPath(new File(path));
    if (canonicalPath == null) return;
    markAsUpToDate(project, true, canonicalPath);
  }

  public static String getCanonicalPath(File file) {
    if (SystemInfo.isFileSystemCaseSensitive) {
      return file.getAbsolutePath().replace(File.separatorChar, '/');
    }
    else {
      try {
        return file.getCanonicalPath().replace(File.separatorChar, '/');
      }
      catch (IOException e) {
        return null;
      }
    }
  }

  public static VcsSelection getSelection(VcsContext context) {

    VcsSelection selectionFromEditor = getSelectionFromEditor(context);
    if (selectionFromEditor != null) {
      return selectionFromEditor;
    }
    return getSelectionFromPsiElement(context);
  }

  private static VcsSelection getSelectionFromPsiElement(VcsContext context) {
    PsiElement psiElement = context.getPsiElement();
    if (psiElement == null) {
      return null;
    }
    if (!psiElement.isValid()) {
      return null;
    }

    final String actionName;

    if (psiElement instanceof PsiClass) {
      actionName = LocalVcsBundle.message("action.name.show.history.for.class");
    } else if (psiElement instanceof PsiField) {
      actionName = LocalVcsBundle.message("action.name.show.history.for.field");
    } else if (psiElement instanceof PsiMethod) {
      actionName = LocalVcsBundle.message("action.name.show.history.for.method");
    } else if (psiElement instanceof XmlTag) {
      actionName = LocalVcsBundle.message("action.name.show.history.for.tag");
    } else if (psiElement instanceof XmlText) {
      actionName = LocalVcsBundle.message("action.name.show.history.for.text");
    } else if (psiElement instanceof PsiCodeBlock) {
      actionName = LocalVcsBundle.message("action.name.show.history.for.code.block");
    } else if (psiElement instanceof PsiStatement) {
      actionName = LocalVcsBundle.message("action.name.show.history.for.statement");
    } else {
      return null;
    }

    TextRange textRange = psiElement.getTextRange();
    if (textRange == null) {
      return null;
    }

    VirtualFile virtualFile = psiElement.getContainingFile().getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    if (!virtualFile.isValid()) {
      return null;
    }

    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    return new VcsSelection(document, textRange, actionName);
  }

  private static VcsSelection getSelectionFromEditor(VcsContext context) {
    Editor editor = context.getEditor();
    if (editor == null) return null;
    SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      return null;
    }
    return new VcsSelection(editor.getDocument(), selectionModel);
  }

  public static AbstractVcs getVcsFor(Project project, FilePath file) {
    ProjectLevelVcsManager projectLevelVcsManager = ProjectLevelVcsManager.getInstance(project);
    VirtualFile virtualFile = file.getVirtualFile();
    VirtualFile virtualFileParent = file.getVirtualFileParent();
    if (virtualFile != null){
      return projectLevelVcsManager.getVcsFor(virtualFile);
    } else if (virtualFileParent != null){
      return projectLevelVcsManager.getVcsFor(virtualFileParent);
    } else {
      return null;
    }
  }

  public static void refreshFiles(final FilePath[] roots, final Runnable runnable) {

    ApplicationManager.getApplication().assertIsDispatchThread();

    refreshFiles(collectFilesToRefresh(roots), runnable);
  }

  private static File[] collectFilesToRefresh(final FilePath[] roots) {
    final File[] result = new File[roots.length];
    for (int i = 0; i < roots.length; i++) {
      result[i] = roots[i].getIOFile();
    }
    return result;
  }

  public static void refreshFiles(final File[] roots, final Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    refreshFiles(collectFilesToRefresh(roots), runnable);
  }

  private static void refreshFiles(final List<VirtualFile> filesToRefresh, final Runnable runnable) {
    if (filesToRefresh.size() == 0) {
      runnable.run();
      return;
    }
    final int[] refreshed = new int[]{0};
    final Runnable afterRefresh = new Runnable() {
      public void run() {
        synchronized (refreshed) {
          refreshed[0] += 1;
          if (refreshed[0] == filesToRefresh.size()) {
            runnable.run();
          }
        }
      }
    };


    for (VirtualFile file : filesToRefresh) {
      file.refresh(true, true, afterRefresh);
    }
  }

  private static List<VirtualFile> collectFilesToRefresh(final File[] roots) {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (File root : roots) {
      VirtualFile vFile = findFileFor(root);
      if (vFile != null) {
        for (Iterator<VirtualFile> iterator = result.iterator(); iterator.hasNext();) {
          VirtualFile existing = iterator.next();
          if (VfsUtil.isAncestor(existing, vFile, false)) break;
          if (VfsUtil.isAncestor(vFile, existing, false)) {
            iterator.remove();
          }
        }
        result.add(vFile);
      }
    }
    return result;
  }

  private static VirtualFile findFileFor(final File root) {
    File current = root;
    while (current != null) {
      final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(root);
      if (vFile != null) return vFile;
      current = current.getParentFile();
    }

    return null;
  }

  public static VirtualFile getVirtualFile( final String path ) {
    return ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().findFileByPath( path.replace( File.separatorChar, '/' ));
      }
    });
  }

  public static boolean isPathUnderProject( Project project, final String path )
  {
    VirtualFile vfPath = getVirtualFile( path );
    return isPathUnderProject( project, vfPath );
  }

  public static boolean isPathUnderProject( Project project, final VirtualFile vf )
  {
    if( vf != null && !FileTypeManager.getInstance().isFileIgnored( vf.getPath() ) )
    {
      Module mod = ProjectRootManager.getInstance( project ).getFileIndex().getModuleForFile( vf );
      return mod != null;
    }
    return false;
  }
}
