// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.diff;

import com.intellij.diff.util.DiffUtil;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.pom.Navigatable;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author Konstantin Bulenkov
 */
public class VirtualFileDiffElement extends DiffElement<VirtualFile> {
  private final static Logger LOGGER = Logger.getInstance(VirtualFileDiffElement.class);
  private final VirtualFile myFile;
  protected final VirtualFile myDiffRoot;

  public VirtualFileDiffElement(@NotNull VirtualFile file, @Nullable VirtualFile diffRoot) {
    myFile = file;
    myDiffRoot = diffRoot;
  }

  public VirtualFileDiffElement(@NotNull VirtualFile file) {
    this(file, null);
  }

  @Override
  public String getPath() {
    return myFile.getPresentableUrl();
  }

  @NotNull
  @Override
  public String getName() {
    return myFile.getName();
  }

  @Override
  public String getPresentablePath() {
    return getPath();
  }

  @Override
  public long getSize() {
    return myFile.getLength();
  }

  @Override
  public long getTimeStamp() {
    return myFile.getTimeStamp();
  }

  @Override
  public boolean isContainer() {
    return myFile.isDirectory();
  }

  public VirtualFile getDiffRoot() {
    return myDiffRoot;
  }

  @Override
  @Nullable
  public Navigatable getNavigatable(@Nullable Project project) {
    if (project == null || project.isDefault() || !myFile.isValid()) return null;
    return new OpenFileDescriptor(project, myFile);
  }

  @Override
  public VirtualFileDiffElement[] getChildren() {
    if (myFile.is(VFileProperty.SYMLINK)) {
      return new VirtualFileDiffElement[0];
    }
    final VirtualFile[] files = myFile.getChildren();
    final ArrayList<VirtualFileDiffElement> elements = new ArrayList<>();
    for (VirtualFile file : files) {
      if (!FileTypeManager.getInstance().isFileIgnored(file) && file.isValid()) {
        elements.add(new VirtualFileDiffElement(file, myDiffRoot));
      }
    }
    return elements.toArray(new VirtualFileDiffElement[0]);
  }

  @Override
  public byte @Nullable [] getContent() throws IOException {
    return ReadAction.compute(() -> myFile.contentsToByteArray());
  }

  @Nullable
  @Override
  public InputStream getContentStream() throws IOException {
    return DiffUtil.getFileInputStream(myFile);
  }

  @Override
  public VirtualFile getValue() {
    return myFile;
  }

  @Override
  public Icon getIcon() {
    return isContainer() ? PlatformIcons.FOLDER_ICON : VirtualFilePresentation.getIcon(myFile);
  }

  @Override
  public Callable<DiffElement<VirtualFile>> getElementChooser(final Project project) {
    return () -> {
      final FileChooserDescriptor descriptor = getChooserDescriptor();
      final VirtualFile[] result = FileChooser.chooseFiles(descriptor, project, getValue());
      return result.length == 1 ? createElement(result[0], myDiffRoot) : null;
    };
  }

  @NotNull
  public static VirtualFileDiffElement createElement(VirtualFile file, VirtualFile diffRoot) {
    if (file.getFileType() instanceof ArchiveFileType &&
        file.getFileSystem() != JarFileSystem.getInstance()) {
      VirtualFile jar = JarFileSystem.getInstance().getJarRootForLocalFile(file);
      if (jar != null) {
        return new VirtualFileDiffElement(jar, diffRoot);
      }
    }
    return new VirtualFileDiffElement(file, diffRoot);
  }

  @Override
  public String getFilterablePath() {
    if (myDiffRoot == null) {
      return super.getFilterablePath();
    }
    String path = myFile.getPath();
    if (myFile.equals(myDiffRoot)) {
      return path;
    }
    String rootPath = myDiffRoot.getPath();
    String relativePath = path;
    if (path.startsWith(rootPath) && rootPath.length() + 1 <= path.length()) {
      String rawRelativePath = path.substring(rootPath.length());
      if (rawRelativePath.startsWith("!")) {
        relativePath = rawRelativePath.substring(2);
      }
      else {
        relativePath = rawRelativePath.substring(1);
      }
    }
    return relativePath;
  }

  protected FileChooserDescriptor getChooserDescriptor() {
    return new FileChooserDescriptor(true, true, true, true, false, false).withExtensionFilter(ArchiveFileType.INSTANCE);
  }

  @Override
  public boolean isOperationsEnabled() {
    return myFile.getFileSystem() instanceof LocalFileSystem;
  }

  @NotNull
  @Override
  public Charset getCharset() {
    return myFile.getCharset();
  }

  @Override
  public FileType getFileType() {
    return myFile.getFileType();
  }

  @Override
  public VirtualFileDiffElement copyTo(DiffElement<VirtualFile> container, String relativePath) {
    try {
      final File src = new File(myFile.getPath());
      final Path targetPath = Paths.get(container.getValue().getPath(), relativePath, src.getName());
      final File trg = targetPath.toFile();
      FileUtil.copy(src, trg);
      final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(trg);
      if (virtualFile != null) {
        VirtualFile diffRoot = container instanceof VirtualFileDiffElement ? ((VirtualFileDiffElement)container).getDiffRoot() : null;
        return new VirtualFileDiffElement(virtualFile, diffRoot);
      }
    }
    catch (InvalidPathException e) {
      LOGGER.error(e);
    }
    catch (IOException e) {//
    }
    return null;
  }

  @Override
  public boolean delete() {
    try {
      myFile.delete(this);
    }
    catch (IOException e) {
      return false;
    }
    return true;
  }

  @Override
  public void refresh(boolean userInitiated) {
    refreshFile(userInitiated, myFile);
  }

  public static void refreshFile(boolean userInitiated, VirtualFile virtualFile) {
    if (userInitiated) {
      final List<Document> docsToSave = new ArrayList<>();
      final FileDocumentManager manager = FileDocumentManager.getInstance();
      for (Document document : manager.getUnsavedDocuments()) {
        VirtualFile file = manager.getFile(document);
        if (file != null && VfsUtilCore.isAncestor(virtualFile, file, false)) {
          docsToSave.add(document);
        }
      }

      if (!docsToSave.isEmpty()) {
        WriteAction.runAndWait(() -> {
          for (Document document : docsToSave) {
            manager.saveDocument(document);
          }
        });
      }

      ModalityState modalityState = ProgressManager.getInstance().getProgressIndicator().getModalityState();

      VfsUtil.markDirty(true, true, virtualFile);
      RefreshQueue.getInstance().refresh(false, true, null, modalityState, virtualFile);
    }
  }
}
