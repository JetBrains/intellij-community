/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.LineSeparator;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Represents a file in <code>{@link VirtualFileSystem}</code>. A particular file is represented by equal
 * {@code VirtualFile} instances for the entire lifetime of the IntelliJ IDEA process, unless the file
 * is deleted, in which case {@link #isValid()} will return {@code false}.
 * <p/>
 * VirtualFile instances are created on request, so there can be several instances corresponding to the same file.
 * All of them are equal, have the same hashCode and use shared storage for all related data, including user data (see {@link UserDataHolder}).
 * <p/>
 * If an in-memory implementation of VirtualFile is required, {@link LightVirtualFile}
 * can be used.
 * <p/>
 * Please see <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/virtual_file_system.html">IntelliJ IDEA Virtual File System</a>
 * for high-level overview.
 *
 * @see VirtualFileSystem
 * @see VirtualFileManager
 */
public abstract class VirtualFile extends UserDataHolderBase implements ModificationTracker {
  public static final Key<Object> REQUESTOR_MARKER = Key.create("REQUESTOR_MARKER");
  public static final VirtualFile[] EMPTY_ARRAY = new VirtualFile[0];

  /**
   * Used as a property name in the {@link VirtualFilePropertyEvent} fired when the name of a
   * {@link VirtualFile} changes.
   *
   * @see VirtualFileListener#propertyChanged
   * @see VirtualFilePropertyEvent#getPropertyName
   */
  public static final String PROP_NAME = "name";

  /**
   * Used as a property name in the {@link VirtualFilePropertyEvent} fired when the encoding of a
   * {@link VirtualFile} changes.
   *
   * @see VirtualFileListener#propertyChanged
   * @see VirtualFilePropertyEvent#getPropertyName
   */
  public static final String PROP_ENCODING = "encoding";

  /**
   * Used as a property name in the {@link VirtualFilePropertyEvent} fired when the write permission of a
   * {@link VirtualFile} changes.
   *
   * @see VirtualFileListener#propertyChanged
   * @see VirtualFilePropertyEvent#getPropertyName
   */
  public static final String PROP_WRITABLE = "writable";

  /**
   * Used as a property name in the {@link VirtualFilePropertyEvent} fired when a visibility of a
   * {@link VirtualFile} changes.
   *
   * @see VirtualFileListener#propertyChanged
   * @see VirtualFilePropertyEvent#getPropertyName
   */
  public static final String PROP_HIDDEN = "HIDDEN";

  /**
   * Used as a property name in the {@link VirtualFilePropertyEvent} fired when a symlink target of a
   * {@link VirtualFile} changes.
   *
   * @see VirtualFileListener#propertyChanged
   * @see VirtualFilePropertyEvent#getPropertyName
   */
  public static final String PROP_SYMLINK_TARGET = "symlink";

  /**
   * Acceptable values for "propertyName" argument in
   * {@link VFilePropertyChangeEvent#VFilePropertyChangeEvent(Object, VirtualFile, String, Object, Object, boolean)}
   */
  @MagicConstant(stringValues = {PROP_NAME, PROP_ENCODING, PROP_HIDDEN, PROP_WRITABLE, PROP_SYMLINK_TARGET})
  public @interface PropName {}

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.VirtualFile");
  private static final Key<byte[]> BOM_KEY = Key.create("BOM");
  private static final Key<Charset> CHARSET_KEY = Key.create("CHARSET");

  protected VirtualFile() { }

  /**
   * Gets the name of this file.
   *
   * @see #getNameSequence()
   */
  @NotNull
  public abstract String getName();

  @NotNull
  public CharSequence getNameSequence() {
    return getName();
  }

  /**
   * Gets the {@link VirtualFileSystem} this file belongs to.
   *
   * @return the {@link VirtualFileSystem}
   */
  @NotNull
  public abstract VirtualFileSystem getFileSystem();

  /**
   * Gets the path of this file. Path is a string which uniquely identifies file within given
   * <code>{@link VirtualFileSystem}</code>. Format of the path depends on the concrete file system.
   * For <code>{@link com.intellij.openapi.vfs.LocalFileSystem}</code> it is an absolute file path with file separator characters
   * (File.separatorChar) replaced to the forward slash ('/').
   *
   * @return the path
   */
  @SuppressWarnings("JavadocReference")
  @NotNull
  public abstract String getPath();

  /**
   * Gets the URL of this file. The URL is a string which uniquely identifies file in all file systems.
   * It has the following format: {@code <protocol>://<path>}.
   * <p>
   * File can be found by its URL using {@link VirtualFileManager#findFileByUrl} method.
   * <p>
   * Please note these URLs are intended for use withing VFS - meaning they are not necessarily RFC-compliant.
   *
   * @return the URL consisting of protocol and path
   * @see VirtualFileManager#findFileByUrl
   * @see VirtualFile#getPath
   * @see VirtualFileSystem#getProtocol
   */
  @NotNull
  public String getUrl() {
    return VirtualFileManager.constructUrl(getFileSystem().getProtocol(), getPath());
  }

  /**
   * Fetches "presentable URL" of this file. "Presentable URL" is a string to be used for displaying this
   * file in the UI.
   *
   * @return the presentable URL.
   * @see VirtualFileSystem#extractPresentableUrl
   */
  @NotNull
  public final String getPresentableUrl() {
    return getFileSystem().extractPresentableUrl(getPath());
  }

  /**
   * Gets the extension of this file. If file name contains '.' extension is the substring from the last '.'
   * to the end of the name, otherwise extension is null.
   *
   * @return the extension or null if file name doesn't contain '.'
   */
  @Nullable
  public String getExtension() {
    CharSequence extension = FileUtilRt.getExtension(getNameSequence(), null);
    return extension == null ? null : extension.toString();
  }

  /**
   * Gets the file name without the extension. If file name contains '.' the substring till the last '.' is returned.
   * Otherwise the same value as <code>{@link #getName}</code> method returns is returned.
   *
   * @return the name without extension
   *         if there is no '.' in it
   */
  @NotNull
  public String getNameWithoutExtension() {
    return FileUtilRt.getNameWithoutExtension(getNameSequence()).toString();
  }

  /**
   * Renames this file to the {@code newName}.<p>
   * This method should be only called within write-action.
   * See {@link Application#runWriteAction(Runnable)}.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if {@code requestor} is {@code null}.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param newName   the new file name
   * @throws IOException if file failed to be renamed
   */
  public void rename(Object requestor, @NotNull String newName) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (getName().equals(newName)) return;
    if (!getFileSystem().isValidName(newName)) {
      throw new IOException(VfsBundle.message("file.invalid.name.error", newName));
    }

    getFileSystem().renameFile(requestor, this, newName);
  }

  /**
   * Checks whether this file has write permission. Note that this value may be cached and may differ from
   * the write permission of the physical file.
   *
   * @return {@code true} if this file is writable, {@code false} otherwise
   */
  public abstract boolean isWritable();

  public void setWritable(boolean writable) throws IOException {
    throw new IOException("Not supported");
  }

  /**
   * Checks whether this file is a directory.
   *
   * @return {@code true} if this file is a directory, {@code false} otherwise
   */
  public abstract boolean isDirectory();

  /**
   * Checks whether this file has a specific property.
   *
   * @return {@code true} if the file has a specific property, {@code false} otherwise
   * @since 13.0
   */
  public boolean is(@NotNull VFileProperty property) {
    return false;
  }

  /**
   * Resolves all symbolic links containing in a path to this file and returns a path to a link target (in platform-independent format).
   * <p/>
   * <b>Note</b>: please use this method judiciously. In most cases VFS clients don't need to resolve links in paths and should
   * work with those provided by a user.
   *
   * @return {@code getPath()} if there are no symbolic links in a file's path;
   *         {@code getCanonicalFile().getPath()} if the link was successfully resolved;
   *         {@code null} otherwise
   * @since 11.1
   */
  @Nullable
  public String getCanonicalPath() {
    return getPath();
  }

  /**
   * Resolves all symbolic links containing in a path to this file and returns a link target.
   * <p/>
   * <b>Note</b>: please use this method judiciously. In most cases VFS clients don't need to resolve links in paths and should
   * work with those provided by a user.
   *
   * @return {@code this} if there are no symbolic links in a file's path;
   *         instance of {@code VirtualFile} if the link was successfully resolved;
   *         {@code null} otherwise
   * @since 11.1
   */
  @Nullable
  public VirtualFile getCanonicalFile() {
    return this;
  }

  /**
   * Checks whether this {@code VirtualFile} is valid. File can be invalidated either by deleting it or one of its
   * parents with {@link #delete} method or by an external change.
   * If file is not valid only {@link #equals}, {@link #hashCode} and methods from
   * {@link UserDataHolder} can be called for it. Using any other methods for an invalid {@link VirtualFile} instance
   * produce unpredictable results.
   *
   * @return {@code true} if this is a valid file, {@code false} otherwise
   */
  public abstract boolean isValid();

  /**
   * Gets the parent {@code VirtualFile}.
   *
   * @return the parent file or {@code null} if this file is a root directory
   */
  public abstract VirtualFile getParent();

  /**
   * Gets the child files.
   *
   * @return array of the child files or {@code null} if this file is not a directory
   */
  public abstract VirtualFile[] getChildren();

  /**
   * Finds child of this file with the given name.
   *
   * @param name the file name to search by
   * @return the file if found any, {@code null} otherwise
   */
  @Nullable
  public VirtualFile findChild(@NotNull String name) {
    VirtualFile[] children = getChildren();
    if (children == null) return null;
    for (VirtualFile child : children) {
      if (child.nameEquals(name)) {
        return child;
      }
    }
    return null;
  }

  @NotNull
  public VirtualFile findOrCreateChildData(Object requestor, @NotNull String name) throws IOException {
    final VirtualFile child = findChild(name);
    if (child != null) return child;
    return createChildData(requestor, name);
  }

  /**
   * @return the {@link FileType} of this file.
   *         When IDEA has no idea what the file type is (i.e. file type is not registered via {@link FileTypeRegistry}),
   *         it returns {@link com.intellij.openapi.fileTypes.FileTypes#UNKNOWN}
   */
  @SuppressWarnings("JavadocReference")
  @NotNull
  public FileType getFileType() {
    return FileTypeRegistry.getInstance().getFileTypeByFile(this);
  }

  /**
   * Finds file by path relative to this file.
   *
   * @param relPath the relative path with / used as separators
   * @return the file if found any, {@code null} otherwise
   */
  @Nullable
  public VirtualFile findFileByRelativePath(@NotNull String relPath) {
    if (relPath.isEmpty()) return this;
    relPath = StringUtil.trimStart(relPath, "/");

    int index = relPath.indexOf('/');
    if (index < 0) index = relPath.length();
    String name = relPath.substring(0, index);

    VirtualFile child;
    if (name.equals(".")) {
      child = this;
    }
    else if (name.equals("..")) {
      if (is(VFileProperty.SYMLINK)) {
        final VirtualFile canonicalFile = getCanonicalFile();
        child = canonicalFile != null ? canonicalFile.getParent() : null;
      }
      else {
        child = getParent();
      }
    }
    else {
      child = findChild(name);
    }

    if (child == null) return null;

    if (index < relPath.length()) {
      return child.findFileByRelativePath(relPath.substring(index + 1));
    }
    return child;
  }

  /**
   * Creates a subdirectory in this directory. This method should be only called within write-action.
   * See {@link Application#runWriteAction}.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if {@code requestor} is {@code null}.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param name      directory name
   * @return {@code VirtualFile} representing the created directory
   * @throws IOException if directory failed to be created
   */
  @NotNull
  public VirtualFile createChildDirectory(Object requestor, @NotNull String name) throws IOException {
    if (!isDirectory()) {
      throw new IOException(VfsBundle.message("directory.create.wrong.parent.error"));
    }

    if (!isValid()) {
      throw new IOException(VfsBundle.message("invalid.directory.create.files"));
    }

    if (!getFileSystem().isValidName(name)) {
      throw new IOException(VfsBundle.message("directory.invalid.name.error", name));
    }

    if (findChild(name) != null) {
      throw new IOException(VfsBundle.message("file.create.already.exists.error", getUrl(), name));
    }

    return getFileSystem().createChildDirectory(requestor, this, name);
  }

  /**
   * Creates a new file in this directory. This method should be only called within write-action.
   * See {@link Application#runWriteAction}.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if {@code requestor} is {@code null}.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @return {@code VirtualFile} representing the created file
   * @throws IOException if file failed to be created
   */
  @NotNull
  public VirtualFile createChildData(Object requestor, @NotNull String name) throws IOException {
    if (!isDirectory()) {
      throw new IOException(VfsBundle.message("file.create.wrong.parent.error"));
    }

    if (!isValid()) {
      throw new IOException(VfsBundle.message("invalid.directory.create.files"));
    }

    if (!getFileSystem().isValidName(name)) {
      throw new IOException(VfsBundle.message("file.invalid.name.error", name));
    }

    if (findChild(name) != null) {
      throw new IOException(VfsBundle.message("file.create.already.exists.error", getUrl(), name));
    }

    return getFileSystem().createChildFile(requestor, this, name);
  }

  /**
   * Deletes this file. This method should be only called within write-action.
   * See {@link Application#runWriteAction}.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if {@code requestor} is {@code null}.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @throws IOException if file failed to be deleted
   */
  public void delete(Object requestor) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    LOG.assertTrue(isValid(), "Deleting invalid file");
    getFileSystem().deleteFile(requestor, this);
  }

  /**
   * Moves this file to another directory. This method should be only called within write-action.
   * See {@link Application#runWriteAction}.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if {@code requestor} is {@code null}.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param newParent the directory to move this file to
   * @throws IOException if file failed to be moved
   */
  public void move(final Object requestor, @NotNull final VirtualFile newParent) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(VfsBundle.message("file.move.error", newParent.getPresentableUrl()));
    }

    EncodingRegistry.doActionAndRestoreEncoding(this, () -> {
      getFileSystem().moveFile(requestor, VirtualFile.this, newParent);
      return VirtualFile.this;
    });
  }

  public VirtualFile copy(final Object requestor, @NotNull final VirtualFile newParent, @NotNull final String copyName) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(VfsBundle.message("file.copy.error", newParent.getPresentableUrl()));
    }

    if (!newParent.isDirectory()) {
      throw new IOException(VfsBundle.message("file.copy.target.must.be.directory"));
    }

    return EncodingRegistry.doActionAndRestoreEncoding(this,
                                                       () -> getFileSystem().copyFile(requestor, VirtualFile.this, newParent, copyName));
  }

  /**
   * @return Retrieve the charset file has been loaded with (if loaded) and would be saved with (if would).
   */
  @NotNull
  public Charset getCharset() {
    Charset charset = getStoredCharset();
    if (charset == null) {
      charset = EncodingRegistry.getInstance().getDefaultCharset();
      setCharset(charset);
    }
    return charset;
  }

  @Nullable
  protected Charset getStoredCharset() {
    return getUserData(CHARSET_KEY);
  }

  protected void storeCharset(Charset charset) {
    putUserData(CHARSET_KEY, charset);
  }

  public void setCharset(final Charset charset) {
    setCharset(charset, null);
  }

  public void setCharset(final Charset charset, @Nullable Runnable whenChanged) {
    setCharset(charset, whenChanged, true);
  }

  public void setCharset(final Charset charset, @Nullable Runnable whenChanged, boolean fireEventsWhenChanged) {
    final Charset old = getStoredCharset();
    storeCharset(charset);
    if (Comparing.equal(charset, old)) return;
    byte[] bom = charset == null ? null : CharsetToolkit.getMandatoryBom(charset);
    byte[] existingBOM = getBOM();
    if (bom == null && charset != null && existingBOM != null) {
      bom = CharsetToolkit.canHaveBom(charset, existingBOM) ? existingBOM : null;
    }
    setBOM(bom);

    if (old != null) { //do not send on detect
      if (whenChanged != null) whenChanged.run();
      if (fireEventsWhenChanged) {
        VirtualFileManager.getInstance().notifyPropertyChanged(this, PROP_ENCODING, old, charset);
      }
    }
  }

  public boolean isCharsetSet() {
    return getStoredCharset() != null;
  }

  public final void setBinaryContent(@NotNull byte[] content) throws IOException {
    setBinaryContent(content, -1, -1);
  }

  public void setBinaryContent(@NotNull byte[] content, long newModificationStamp, long newTimeStamp) throws IOException {
    setBinaryContent(content, newModificationStamp, newTimeStamp, this);
  }

  public void setBinaryContent(@NotNull byte[] content, long newModificationStamp, long newTimeStamp, Object requestor) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    try (OutputStream outputStream = getOutputStream(requestor, newModificationStamp, newTimeStamp)) {
      outputStream.write(content);
      outputStream.flush();
    }
  }

  /**
   * Creates the {@code OutputStream} for this file.
   * Writes BOM first, if there is any. See <a href=http://unicode.org/faq/utf_bom.html>Unicode Byte Order Mark FAQ</a> for an explanation.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if {@code requestor} is {@code null}.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @return {@code OutputStream}
   * @throws IOException if an I/O error occurs
   */
  public final OutputStream getOutputStream(Object requestor) throws IOException {
    return getOutputStream(requestor, -1, -1);
  }

  /**
   * Gets the {@code OutputStream} for this file and sets modification stamp and time stamp to the specified values
   * after closing the stream.<p>
   * <p/>
   * Normally you should not use this method.
   *
   * Writes BOM first, if there is any. See <a href=http://unicode.org/faq/utf_bom.html>Unicode Byte Order Mark FAQ</a> for an explanation.
   *
   * @param requestor            any object to control who called this method. Note that
   *                             it is considered to be an external change if {@code requestor} is {@code null}.
   *                             See {@link VirtualFileEvent#getRequestor}
   * @param newModificationStamp new modification stamp or -1 if no special value should be set
   * @param newTimeStamp         new time stamp or -1 if no special value should be set
   * @return {@code OutputStream}
   * @throws IOException if an I/O error occurs
   * @see #getModificationStamp()
   */
  @NotNull
  public abstract OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException;

  /**
   * Returns file content as an array of bytes.
   * Has the same effect as contentsToByteArray(true).
   *
   * @return file content
   * @throws IOException if an I/O error occurs
   * @see #contentsToByteArray(boolean)
   * @see #getInputStream()
   */
  @NotNull
  public abstract byte[] contentsToByteArray() throws IOException;

  /**
   * Returns file content as an array of bytes.
   *
   * @param cacheContent set true to
   * @return file content
   * @throws IOException if an I/O error occurs
   * @see #contentsToByteArray()
   */
  @NotNull
  public byte[] contentsToByteArray(boolean cacheContent) throws IOException {
    return contentsToByteArray();
  }


  /**
   * Gets modification stamp value. Modification stamp is a value changed by any modification
   * of the content of the file. Note that it is not related to the file modification time.
   *
   * @return modification stamp
   * @see #getTimeStamp()
   */
  public long getModificationStamp() {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Gets the timestamp for this file. Note that this value may be cached and may differ from
   * the timestamp of the physical file.
   *
   * @return timestamp
   * @see File#lastModified
   */
  public abstract long getTimeStamp();

  /**
   * File length in bytes.
   *
   * @return the length of this file.
   */
  public abstract long getLength();

  /**
   * Refreshes the cached file information from the physical file system. If this file is not a directory
   * the timestamp value is refreshed and {@code contentsChanged} event is fired if it is changed.<p>
   * If this file is a directory the set of its children is refreshed. If recursive value is {@code true} all
   * children are refreshed recursively.
   * <p/>
   * When invoking synchronous refresh from a thread other than the event dispatch thread, the current thread must
   * NOT be in a read action, otherwise a deadlock may occur.
   *
   * @param asynchronous if {@code true}, the method will return immediately and the refresh will be processed
   *                     in the background. If {@code false}, the method will return only after the refresh
   *                     is done and the VFS change events caused by the refresh have been fired and processed
   *                     in the event dispatch thread. Instead of synchronous refreshes, it's recommended to use
   *                     asynchronous refreshes with a {@code postRunnable} whenever possible.
   * @param recursive    whether to refresh all the files in this directory recursively
   */
  public void refresh(boolean asynchronous, boolean recursive) {
    refresh(asynchronous, recursive, null);
  }

  /**
   * The same as {@link #refresh(boolean, boolean)} but also runs {@code postRunnable}
   * after the operation is completed. The runnable is executed on event dispatch thread inside a write action.
   */
  public abstract void refresh(boolean asynchronous, boolean recursive, @Nullable Runnable postRunnable);

  public String getPresentableName() {
    return getName();
  }

  @Override
  public long getModificationCount() {
    return isValid() ? getTimeStamp() : -1;
  }

  /**
   * @return whether file name equals to this name
   *         result depends on the filesystem specifics
   */
  protected boolean nameEquals(@NotNull String name) {
    return getName().equals(name);
  }

  /**
   * Gets the {@code InputStream} for this file.
   * Skips BOM if there is any. See <a href=http://unicode.org/faq/utf_bom.html>Unicode Byte Order Mark FAQ</a> for an explanation.
   *
   * @return {@code InputStream}
   * @throws IOException if an I/O error occurs
   * @see #contentsToByteArray
   */
  public abstract InputStream getInputStream() throws IOException;

  @Nullable
  public byte[] getBOM() {
    return getUserData(BOM_KEY);
  }

  public void setBOM(@Nullable byte[] BOM) {
    putUserData(BOM_KEY, BOM);
  }

  @Override
  public String toString() {
    return "VirtualFile: " + getPresentableUrl();
  }

  public boolean exists() {
    return isValid();
  }

  public boolean isInLocalFileSystem() {
    return false;
  }

  /** @deprecated use {@link VirtualFileSystem#isValidName(String)} (to be removed in IDEA 18) */
  public static boolean isValidName(@NotNull String name) {
    return !name.isEmpty() && name.indexOf('\\') < 0 && name.indexOf('/') < 0;
  }

  private static final Key<String> DETECTED_LINE_SEPARATOR_KEY = Key.create("DETECTED_LINE_SEPARATOR_KEY");

  /**
   * @return Line separator for this file.
   * It is always null for directories and binaries, and possibly null if a separator isn't yet known.
   * @see LineSeparator
   */
  @Nullable
  public String getDetectedLineSeparator() {
    return getUserData(DETECTED_LINE_SEPARATOR_KEY);
  }

  public void setDetectedLineSeparator(@Nullable String separator) {
    putUserData(DETECTED_LINE_SEPARATOR_KEY, separator);
  }

  public void setPreloadedContentHint(byte[] preloadedContentHint) { }
}