// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Objects;

public class LocalFilePath implements FilePath {
  private static final Logger LOG = Logger.getInstance(LocalFilePath.class);

  @NotNull
  @SystemIndependent
  private final String myPath;
  private final boolean myIsDirectory;
  private VirtualFile myCachedFile;

  public LocalFilePath(@NotNull String path, boolean isDirectory) {
    myPath = FileUtil.toCanonicalPath(path);
    myIsDirectory = isDirectory;

    if (myPath.isEmpty()) {
      LOG.error(new Throwable("Invalid empty file path: '" + path + "'"));
    }
  }

  public LocalFilePath(@NotNull Path path, boolean isDirectory) {
    this(path.toAbsolutePath().toString(), isDirectory);
  }

  protected LocalFilePath(@NotNull String path, boolean isDirectory,
                          @SuppressWarnings("unused") @Nullable Void privateConstructorMarker) {
    myPath = path;
    myIsDirectory = isDirectory;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LocalFilePath path = (LocalFilePath)o;

    if (myIsDirectory != path.myIsDirectory) {
      return false;
    }
    if (myPath.equals(path.myPath)) {
      return true;
    }
    if (!myPath.equalsIgnoreCase(path.myPath)) {
      return false;
    }
    // make sure to not query (expensive) getVirtualFile() until it's absolutely necessary, e.g. we encountered two file paths differ by case only
    VirtualFile file = getVirtualFile();
    VirtualFile oFile = path.getVirtualFile();
    if (file == null && oFile == null) return !SystemInfo.isFileSystemCaseSensitive;
    return Objects.equals(file, oFile);
  }

  @Override
  public int hashCode() {
    int result = Strings.stringHashCodeInsensitive(myPath);
    result = 31 * result + (myIsDirectory ? 1 : 0);
    return result;
  }

  @Override
  public void refresh() {
  }

  @Override
  public void hardRefresh() {
    LocalFileSystem.getInstance().refreshAndFindFileByPath(myPath);
  }

  /**
   * The paths are the same as reported by {@link VirtualFile#getPath()}.
   * <p>
   * Most paths will not have a trailing '/' with an exception to file system roots: "C:/" and "/".
   * For this reason, the paths are never empty.
   */
  @NotNull
  @Override
  public String getPath() {
    return myPath;
  }

  @Override
  public boolean isDirectory() {
    return myIsDirectory;
  }

  @Override
  public boolean isUnder(@NotNull FilePath parent, boolean strict) {
    return FileUtil.startsWith(this.getPath(), parent.getPath(), SystemInfo.isFileSystemCaseSensitive, strict);
  }

  @Override
  @Nullable
  public FilePath getParentPath() {
    String parent = PathUtil.getParentPath(myPath);

    if (SystemInfo.isWindows) {
      if (parent.isEmpty()) {
        return null;
      }
      if (!myPath.startsWith("/") && !parent.contains("/")) {
        // make sure we use "C:/" instead of "C:", to match VirtualFile.
        return new LocalFilePath(parent + "/", true, null);
      }
    }
    else {
      if (parent.isEmpty()) {
        if (myPath.length() > 1 && myPath.startsWith("/")) {
          return new LocalFilePath("/", true, null);
        }
        else {
          return null;
        }
      }
    }

    return new LocalFilePath(parent, true, null);
  }

  @Override
  @Nullable
  public VirtualFile getVirtualFile() {
    VirtualFile cachedFile = myCachedFile;
    if (cachedFile == null ||
        !cachedFile.isValid() ||
        !(cachedFile.isCaseSensitive() ? getPath(cachedFile).equals(myPath) : getPath(cachedFile).equalsIgnoreCase(myPath))) {
      myCachedFile = cachedFile = findFile(myPath);
    }
    return cachedFile;
  }

  @Nullable
  protected VirtualFile findFile(@NotNull String path) {
    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  @NotNull
  protected @NonNls String getPath(@NotNull VirtualFile cachedFile) {
    return cachedFile.getPath();
  }

  @Override
  @Nullable
  public VirtualFile getVirtualFileParent() {
    FilePath parent = getParentPath();
    return parent != null ? parent.getVirtualFile() : null;
  }

  @Override
  @NotNull
  public File getIOFile() {
    return new File(myPath);
  }

  @NotNull
  @Override
  public String getName() {
    return PathUtil.getFileName(myPath);
  }

  @NotNull
  @Override
  public String getPresentableUrl() {
    return FileUtil.toSystemDependentName(myPath);
  }

  @Override
  @Nullable
  public Document getDocument() {
    VirtualFile file = getVirtualFile();
    if (file == null || file.getFileType().isBinary()) {
      return null;
    }
    return FileDocumentManager.getInstance().getDocument(file);
  }

  @Override
  @NotNull
  public Charset getCharset() {
    return getCharset(null);
  }

  @Override
  @NotNull
  public Charset getCharset(@Nullable Project project) {
    VirtualFile file = getVirtualFile();
    String path = myPath;
    while ((file == null || !file.isValid()) && !path.isEmpty()) {
      path = PathUtil.getParentPath(path);
      file = findFile(path);
    }
    if (file != null) {
      return file.getCharset();
    }
    EncodingManager e = project == null ? EncodingManager.getInstance() : EncodingProjectManager.getInstance(project);
    return e.getDefaultCharset();
  }

  @Override
  @NotNull
  public FileType getFileType() {
    VirtualFile file = getVirtualFile();
    FileTypeManager manager = FileTypeManager.getInstance();
    return file != null ? manager.getFileTypeByFile(file) : manager.getFileTypeByFileName(getName());
  }

  @Override
  @NonNls
  public String toString() {
    return myPath + (myIsDirectory && !StringUtil.endsWith(myPath, "/") ? "/" : "");
  }

  @Override
  public boolean isNonLocal() {
    return false;
  }
}
