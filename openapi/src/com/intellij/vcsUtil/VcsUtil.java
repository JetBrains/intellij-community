package com.intellij.vcsUtil;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;

import java.io.File;
import java.io.IOException;


public class VcsUtil {

  public static void markFileAsUpToDate(VirtualFile file, Project project) {
    markAsUpToDate(project, file.isDirectory(), file.getPath());
    FileStatusManager.getInstance(project).fileStatusChanged(file);
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

    PsiDocumentManager.getInstance(psiElement.getProject()).commitDocument(document);

    return new VcsSelection(document, textRange);
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
}
