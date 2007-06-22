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

import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class VcsUtil {
  protected static final char[] ourCharsToBeChopped = new char[]{'/', '\\'};


  /**
   * Call "fileDirty" in the read action.
   */
  public static void markFileAsDirty(final Project project, final VirtualFile file) {
    final VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        mgr.fileDirty(file);
      }
    });
  }

  public static void markFileAsDirty(final Project project, final String path) {
    final FilePath filePath = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(new File(path));
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        VcsDirtyScopeManager.getInstance(project).fileDirty(filePath);
      }
    });
  }

  public static void refreshFiles(Project project, HashSet<FilePath> paths) {
    for (FilePath path : paths) {
      VirtualFile vFile = path.getVirtualFile();
      if (vFile != null) {
        if (vFile.isDirectory()) {
          markFileAsDirty(project, vFile);
        }
        else {
          vFile.refresh(true, vFile.isDirectory());
        }
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

  @Nullable
  private static VcsSelection getSelectionFromPsiElement(VcsContext context) {
    PsiElement psiElement = context.getPsiElement();
    if (psiElement == null) {
      return null;
    }
    if (!psiElement.isValid()) {
      return null;
    }
    if (psiElement instanceof PsiCompiledElement) {
      return null;
    }

    final String actionName;

    if (psiElement instanceof PsiClass) {
      actionName = LocalHistoryBundle.message("action.name.show.history.for.class");
    }
    else if (psiElement instanceof PsiField) {
      actionName = LocalHistoryBundle.message("action.name.show.history.for.field");
    }
    else if (psiElement instanceof PsiMethod) {
      actionName = LocalHistoryBundle.message("action.name.show.history.for.method");
    }
    else if (psiElement instanceof XmlTag) {
      actionName = LocalHistoryBundle.message("action.name.show.history.for.tag");
    }
    else if (psiElement instanceof XmlText) {
      actionName = LocalHistoryBundle.message("action.name.show.history.for.text");
    }
    else if (psiElement instanceof PsiCodeBlock) {
      actionName = LocalHistoryBundle.message("action.name.show.history.for.code.block");
    }
    else if (psiElement instanceof PsiStatement) {
      actionName = LocalHistoryBundle.message("action.name.show.history.for.statement");
    }
    else {
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

  /**
   * @param project Project component
   * @param file    File to check
   * @return true if the given file resides under the root associated with any
   */
  public static boolean isFileUnderVcs(Project project, String file) {
    return getVcsFor(project, getFilePath(file)) != null;
  }

  public static boolean isFileUnderVcs(Project project, FilePath file) {
    return getVcsFor(project, file) != null;
  }

  /**
   * File is considered to be a valid vcs file if it resides under the content
   * root controlled by the given vcs.
   */
  public static boolean isFileForVcs(VirtualFile file, Project project, AbstractVcs host) {
    return getVcsFor(project, file) == host;
  }

  //  NB: do not reduce this method to the method above since PLVcsMgr uses
  //      different methods for computing its predicate (since FilePath can
  //      refer to the deleted files).
  public static boolean isFileForVcs(FilePath path, Project project, AbstractVcs host) {
    return getVcsFor(project, path) == host;
  }

  public static boolean isFileForVcs(String path, Project project, AbstractVcs host) {
    return getVcsFor(project, getFilePath(path)) == host;
  }

  @Nullable
  public static AbstractVcs getVcsFor(final Project project, final FilePath file) {
    final AbstractVcs[] vcss = new AbstractVcs[1];
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        vcss[0] = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
      }
    });
    return vcss[0];
  }

  @Nullable
  public static AbstractVcs getVcsFor(final Project project, final VirtualFile file) {
    final AbstractVcs[] vcss = new AbstractVcs[1];
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        vcss[0] = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
      }
    });
    return vcss[0];
  }

  @Nullable
  public static VirtualFile getVcsRootFor(final Project project, final FilePath file) {
    final VirtualFile[] roots = new VirtualFile[1];
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        roots[0] = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file);
      }
    });
    return roots[0];
  }

  public static void refreshFiles(final FilePath[] roots, final Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    refreshFiles(collectFilesToRefresh(roots), runnable);
  }

  public static void refreshFiles(final File[] roots, final Runnable runnable) {
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

  private static void refreshFiles(final List<VirtualFile> filesToRefresh, final Runnable runnable) {
    RefreshQueue.getInstance().refresh(true, true, runnable, filesToRefresh.toArray(new VirtualFile[filesToRefresh.size()]));
  }

  private static List<VirtualFile> collectFilesToRefresh(final File[] roots) {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (File root : roots) {
      VirtualFile vFile = findFileFor(root);
      if (vFile != null) {
        result.add(vFile);
      }
    }
    return result;
  }

  @Nullable
  private static VirtualFile findFileFor(final File root) {
    File current = root;
    while (current != null) {
      final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(root);
      if (vFile != null) return vFile;
      current = current.getParentFile();
    }

    return null;
  }

  @Nullable
  public static VirtualFile getVirtualFile(final String path) {
    return ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
      @Nullable
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().findFileByPath(path.replace(File.separatorChar, '/'));
      }
    });
  }

  @Nullable
  public static VirtualFile getVirtualFile(final File file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
      @Nullable
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().findFileByIoFile(file);
      }
    });
  }

  public static String getFileContent(final String path) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        VirtualFile vFile = VcsUtil.getVirtualFile(path);
        final Document doc = FileDocumentManager.getInstance().getDocument(vFile);
        return doc.getText();
      }
    });
  }

  //  FileDocumentManager has difficulties in loading the content for files
  //  which are outside the project structure?
  public static byte[] getFileByteContent(final File file) throws IOException {
    return ApplicationManager.getApplication().runReadAction(new Computable<byte[]>() {
      public byte[] compute() {
        byte[] content;
        try {
          content = FileUtil.loadFileBytes(file);
        }
        catch (IOException e) {
          content = null;
        }
        return content;
      }
    });
  }

  public static String getFileContent(final File file) throws IOException {
    byte[] content = getFileByteContent(file);
    return new String(content);
  }

  public static boolean isPathUnderProject(Project project, final String path) {
    VirtualFile vfPath = getVirtualFile(path);
    return isPathUnderProject(project, vfPath);
  }

  public static boolean isPathUnderProject(Project project, final VirtualFile vf) {
    if (vf != null && !FileTypeManager.getInstance().isFileIgnored(vf.getPath())) {
      Module mod = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(vf);
      return mod != null;
    }
    return false;
  }

  public static FilePath getFilePath(String path) {
    return getFilePath(new File(path));
  }

  public static FilePath getFilePath(File file) {
    return PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file);
  }

  public static FilePath getFilePath(String path, boolean isDirectory) {
    return getFilePath(new File(path), isDirectory);
  }

  public static FilePath getFilePath(File file, boolean isDirectory) {
    return PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file, isDirectory);
  }

  public static FilePath getFilePathForDeletedFile(String path, boolean isDirectory) {
    return PeerFactory.getInstance().getVcsContextFactory().createFilePathOnDeleted(new File(path), isDirectory);
  }

  /**
   * Shows error message with specified message text and title.
   * The parent component is the root frame.
   *
   * @param project Current project component
   * @param message information message
   * @param title   Dialog title
   */
  public static void showErrorMessage(final Project project, final String message, final String title) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      Messages.showMessageDialog(project, message, title, Messages.getErrorIcon());
    }
    else {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showMessageDialog(project, message, title, Messages.getErrorIcon());
        }
      });
    }
  }

  /**
   * Shows message in the status bar.
   *
   * @param project Current project component
   * @param message information message
   */
  public static void showStatusMessage(final Project project, final String message) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (project.isOpen()) {
          WindowManager.getInstance().getStatusBar(project).setInfo(message);
        }
      }
    });
  }

  /**
   * @param change "Change" description.
   * @return Return true if the "Change" object is created for "Rename" operation:
   *         in this case name of files for "before" and "after" revisions must not
   *         coniside.
   */
  public static boolean isRenameChange(Change change) {
    boolean isRenamed = false;
    ContentRevision before = change.getBeforeRevision();
    ContentRevision after = change.getAfterRevision();
    if (before != null && after != null) {
      String prevFile = getCanonicalLocalPath(before.getFile().getPath());
      String newFile = getCanonicalLocalPath(after.getFile().getPath());
      isRenamed = !prevFile.equals(newFile);
    }
    return isRenamed;
  }

  /**
   * @param change "Change" description.
   * @return Return true if the "Change" object is created for "New" operation:
   *         "before" revision is obviously NULL, while "after" revision is not.
   */
  public static boolean isChangeForNew(Change change) {
    return (change.getBeforeRevision() == null) && (change.getAfterRevision() != null);
  }

  /**
   * @param change "Change" description.
   * @return Return true if the "Change" object is created for "Delete" operation:
   *         "before" revision is NOT NULL, while "after" revision is NULL.
   */
  public static boolean isChangeForDeleted(Change change) {
    return (change.getBeforeRevision() != null) && (change.getAfterRevision() == null);
  }

  public static boolean isChangeForFolder(Change change) {
    ContentRevision revB = change.getBeforeRevision();
    ContentRevision revA = change.getAfterRevision();
    return (revA != null && revA.getFile().isDirectory()) || (revB != null && revB.getFile().isDirectory());
  }

  /**
   * Sort file paths so that paths under the same root are placed from the
   * innermost to the outermost (closest to the root).
   *
   * @param files An array of file paths to be sorted. Sorting is done over the parameter.
   * @return Sorted array of the file paths.
   */
  public static FilePath[] sortPathsFromInnermost(FilePath[] files) {
    return sortPaths(files, -1);
  }

  /**
   * Sort file paths so that paths under the same root are placed from the
   * outermost to the innermost (farest from the root).
   *
   * @param files An array of file paths to be sorted. Sorting is done over the parameter.
   * @return Sorted array of the file paths.
   */
  public static FilePath[] sortPathsFromOutermost(FilePath[] files) {
    return sortPaths(files, 1);
  }

  private static FilePath[] sortPaths(FilePath[] files, final int sign) {
    Arrays.sort(files, new Comparator<FilePath>() {
      public int compare(FilePath o1, FilePath o2) {
        return sign * o1.getPath().compareTo(o2.getPath());
      }
    });
    return files;
  }

  /**
   * @param e ActionEvent object
   * @return <code>VirtualFile</code> available in the current context.
   *         Returns not <code>null</code> if and only if exectly one file is available.
   */
  @Nullable
  public static VirtualFile getOneVirtualFile(AnActionEvent e) {
    VirtualFile[] files = getVirtualFiles(e);
    return (files.length != 1) ? null : files[0];
  }

  /**
   * @param e ActionEvent object
   * @return <code>VirtualFile</code>s available in the current context.
   *         Returns empty array if there are no available files.
   */
  public static VirtualFile[] getVirtualFiles(AnActionEvent e) {
    VirtualFile[] files = e.getData(DataKeys.VIRTUAL_FILE_ARRAY);
    return (files == null) ? VirtualFile.EMPTY_ARRAY : files;
  }

  /**
   * Collects all files which are located in the passed directory.
   *
   * @throws IllegalArgumentException if <code>dir</code> isn't a directory.
   */
  public static void collectFiles(VirtualFile dir, List files, boolean recursive, boolean addDirectories) {
    if (!dir.isDirectory()) {
      throw new IllegalArgumentException(LocalHistoryBundle.message("exception.text.file.should.be.directory", dir.getPresentableUrl()));
    }

    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    VirtualFile[] children = dir.getChildren();
    for (VirtualFile child : children) {
      if (!child.isDirectory() && (fileTypeManager == null || fileTypeManager.getFileTypeByFile(child) != StdFileTypes.UNKNOWN)) {
        files.add(child);
      }
      else if (recursive && child.isDirectory()) {
        if (addDirectories) {
          files.add(child);
        }
        collectFiles(child, files, recursive, false);
      }
    }
  }

  public static boolean runVcsProcessWithProgress(final VcsRunnable runnable, String progressTitle, boolean canBeCanceled, Project project)
    throws VcsException {
    final Ref<VcsException> ex = new Ref<VcsException>();
    boolean result = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        try {
          runnable.run();
        }
        catch (VcsException e) {
          ex.set(e);
        }
      }
    }, progressTitle, canBeCanceled, project);
    if (!ex.isNull()) {
      throw ex.get();
    }
    return result;
  }

  public static VirtualFile waitForTheFile(final String path) {
    final VirtualFile[] file = new VirtualFile[1];
    final Application app = ApplicationManager.getApplication();
    Runnable action = new Runnable() {
      public void run() {
        app.runWriteAction(new Runnable() {
          public void run() {
            file[0] = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
          }
        });
      }
    };

    if (app.isDispatchThread()) {
      action.run();
    }
    else {
      app.invokeAndWait(action, ModalityState.defaultModalityState());
    }

    return file[0];
  }

  public static String getCanonicalLocalPath(String localPath) {
    localPath = chopTrailingChars(localPath.trim().replace('\\', '/'), ourCharsToBeChopped);
    if (localPath.length() == 2 && localPath.charAt(1) == ':') {
      localPath += '/';
    }
    return localPath;
  }

  /**
   * @param source Source string
   * @param chars  Symbols to be trimmed
   * @return string without all specified chars at the end. For example,
   *         <code>chopTrailingChars("c:\\my_directory\\//\\",new char[]{'\\'}) is <code>"c:\\my_directory\\//"</code>,
   *         <code>chopTrailingChars("c:\\my_directory\\//\\",new char[]{'\\','/'}) is <code>"c:\my_directory"</code>.
   *         Actually this method can be used to normalize file names to chop trailing separator chars.
   */
  public static String chopTrailingChars(String source, char[] chars) {
    StringBuffer sb = new StringBuffer(source);
    while (true) {
      boolean atLeastOneCharWasChopped = false;
      for (int i = 0; i < chars.length && sb.length() > 0; i++) {
        if (sb.charAt(sb.length() - 1) == chars[i]) {
          sb.deleteCharAt(sb.length() - 1);
          atLeastOneCharWasChopped = true;
        }
      }
      if (!atLeastOneCharWasChopped) {
        break;
      }
    }
    return sb.toString();
  }

  public static VirtualFile[] paths2VFiles(String[] paths) {
    VirtualFile[] files = new VirtualFile[paths.length];
    for (int i = 0; i < paths.length; i++) {
      files[i] = getVirtualFile(paths[i]);
    }

    return files;
  }
}
