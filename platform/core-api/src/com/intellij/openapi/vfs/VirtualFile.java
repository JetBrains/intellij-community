// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.core.CoreBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayFactory;
import com.intellij.util.LineSeparator;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.text.CharArrayUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * <p>Represents a file in {@link VirtualFileSystem}. A particular file is represented by equal
 * {@code VirtualFile} instances for the entire lifetime of the IDE process, unless the file
 * is deleted, in which case {@link #isValid()} will return {@code false}.</p>
 *
 * <p>VirtualFile instances are created on request, so there can be several instances corresponding to the same file.
 * All of them are equal, have the same {@code hashCode} and use shared storage for all related data, including user data
 * (see {@link UserDataHolder}).</p>
 *
 * <p>If an in-memory implementation of VirtualFile is required, {@link LightVirtualFile} can be used.</p>
 *
 * <p>Please see <a href="https://plugins.jetbrains.com/docs/intellij/virtual-file-system.html">Virtual File System</a>
 * for a high-level overview.</p>
 *
 * @see VirtualFileSystem
 * @see VirtualFileManager
 * @see com.intellij.openapi.vfs.VfsUtil
 */
public abstract class VirtualFile extends UserDataHolderBase implements ModificationTracker {
  public static final VirtualFile[] EMPTY_ARRAY = new VirtualFile[0];
  public static final ArrayFactory<VirtualFile> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new VirtualFile[count];

  /**
   * Used as a property name in the {@link VirtualFilePropertyEvent} fired when the name of a {@link VirtualFile} changes.
   *
   * @see VirtualFileListener#propertyChanged
   * @see VirtualFilePropertyEvent#getPropertyName
   */
  public static final @NonNls String PROP_NAME = "name";

  /**
   * Used as a property name in the {@link VirtualFilePropertyEvent} fired when the encoding of a {@link VirtualFile} changes.
   *
   * @see VirtualFileListener#propertyChanged
   * @see VirtualFilePropertyEvent#getPropertyName
   */
  public static final @NonNls String PROP_ENCODING = "encoding";

  /**
   * Used as a property name in the {@link VirtualFilePropertyEvent} fired when write permission of a {@link VirtualFile} changes.
   *
   * @see VirtualFileListener#propertyChanged
   * @see VirtualFilePropertyEvent#getPropertyName
   */
  public static final @NonNls String PROP_WRITABLE = "writable";

  /**
   * Used as a property name in the {@link VirtualFilePropertyEvent} fired when a visibility of a {@link VirtualFile} changes.
   *
   * @see VirtualFileListener#propertyChanged
   * @see VirtualFilePropertyEvent#getPropertyName
   */
  public static final @NonNls String PROP_HIDDEN = "HIDDEN";

  /**
   * Used as a property name in the {@link VirtualFilePropertyEvent} fired when a symlink target of a {@link VirtualFile} changes.
   *
   * @see VirtualFileListener#propertyChanged
   * @see VirtualFilePropertyEvent#getPropertyName
   */
  public static final @NonNls String PROP_SYMLINK_TARGET = "symlink";

  /**
   * Used as a property name in the {@link VirtualFilePropertyEvent} fired when a case-sensitivity of a {@link VirtualFile} children has changed or became available.
   * After this event the {@link VirtualFile#isCaseSensitive()} may return different value.
   *
   * @see VirtualFileListener#propertyChanged
   * @see VirtualFilePropertyEvent#getPropertyName
   */
  public static final @NonNls String PROP_CHILDREN_CASE_SENSITIVITY = "CHILDREN_CASE_SENSITIVITY";

  /**
   * Acceptable values for "propertyName" argument of {@link VFilePropertyChangeEvent#VFilePropertyChangeEvent VFilePropertyChangeEvent()}.
   */
  @MagicConstant(stringValues = {PROP_NAME, PROP_ENCODING, PROP_HIDDEN, PROP_WRITABLE, PROP_SYMLINK_TARGET, PROP_CHILDREN_CASE_SENSITIVITY})
  public @interface PropName {}

  private static final Logger LOG = Logger.getInstance(VirtualFile.class);
  private static final Key<byte[]> BOM_KEY = Key.create("BOM");
  private static final Key<Charset> CHARSET_KEY = Key.create("CHARSET");

  protected VirtualFile() {
    if (this instanceof Disposable) {
      throw new IllegalStateException("VirtualFile must not implement Disposable because of life-cycle requirements. " +
                                      "E.g. VirtualFile should exist throughout the application and may not be disposed half-way.");
    }
  }

  /**
   * Gets the name of this file.
   *
   * @see #getNameSequence()
   */
  public abstract @NotNull @NlsSafe String getName();

  public @NotNull @NlsSafe CharSequence getNameSequence() {
    return getName();
  }

  /**
   * Gets the {@link VirtualFileSystem} this file belongs to.
   *
   * @return the {@link VirtualFileSystem}
   */
  public abstract @NotNull VirtualFileSystem getFileSystem();

  /**
   * Gets the path of this file. Path is a string that uniquely identifies a file within a given
   * {@link VirtualFileSystem}. Format of the path depends on the concrete file system.
   * For {@link LocalFileSystem} it is an absolute file path with file separator characters
   * ({@link File#separatorChar}) replaced to the forward slash ({@code '/'}). If you need to show path in UI, use {@link #getPresentableUrl()}
   * instead.
   *
   * @return the path
   * @see #toNioPath()
   */
  public abstract @NonNls @NotNull String getPath();

  /**
   * @return a related {@link Path} for a given virtual file where possible otherwise an
   * exception is thrown.
   * The returned {@link Path} may not have a default filesystem behind.
   * <br/>
   * Use {@link #getFileSystem()} and {@link VirtualFileSystem#getNioPath(VirtualFile)}
   * to avoid the exception
   *
   * @throws UnsupportedOperationException if this VirtualFile does not have an associated {@link Path}
   */
  public @NotNull Path toNioPath() {
    Path path = getFileSystem().getNioPath(this);
    if (path == null) {
      throw new UnsupportedOperationException("Failed to map " + this + " (filesystem " + getFileSystem() + ") into nio Path");
    }
    return path;
  }

  /**
   * <p>Returns the URL of this file. The URL is a string that uniquely identifies a file in all file systems.
   * It has the following format: {@code <protocol>://<path>}.</p>
   *
   * <p>File can be found by its URL using {@link VirtualFileManager#findFileByUrl} method.</p>
   *
   * <p>Please note these URLs are intended for use withing VFS - meaning they are not necessarily RFC-compliant. Also it's better not to
   * show them in UI, use {@link #getPresentableUrl()} for that.</p>
   *
   * @return the URL consisting of protocol and path
   * @see VirtualFileManager#findFileByUrl
   * @see VirtualFile#getPath
   * @see VirtualFileSystem#getProtocol
   */
  public @NotNull String getUrl() {
    return VirtualFileManager.constructUrl(getFileSystem().getProtocol(), getPath());
  }

  /**
   * Fetches "presentable URL" of this file. "Presentable URL" is a string to be used for displaying this
   * file in the UI.
   *
   * @return the presentable URL.
   * @see VirtualFileSystem#extractPresentableUrl
   */
  @ApiStatus.NonExtendable
  public @NotNull @NlsSafe String getPresentableUrl() {
    return getFileSystem().extractPresentableUrl(getPath());
  }

  /**
   * Gets the extension of this file. If file name contains '.' extension is the substring from the last '.'
   * to the end of the name (not including the '.'), otherwise extension is null.
   *
   * @return the extension or null if file name doesn't contain '.'
   */
  public @Nullable @NlsSafe String getExtension() {
    CharSequence extension = FileUtilRt.getExtension(getNameSequence(), null);
    return extension == null ? null : extension.toString();
  }

  /**
   * Gets the file name without the extension. If file name contains '.', the substring till the last '.' is returned.
   * Otherwise, the value of {@link #getName} is returned.
   *
   * @return the name without extension
   */
  public @NlsSafe @NotNull String getNameWithoutExtension() {
    return FileUtilRt.getNameWithoutExtension(getNameSequence()).toString();
  }

  /**
   * <p>Renames this file to the {@code newName}.</p>
   *
   * <p>This method should only be called within {@link Application#runWriteAction(Runnable) write action}.</p>
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if {@code requestor} is {@code null}.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param newName   the new file name
   * @throws IOException if file failed to be renamed
   */
  public void rename(Object requestor, @NotNull @NonNls String newName) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (getName().equals(newName)) return;
    if (!getFileSystem().isValidName(newName)) {
      throw new IOException(CoreBundle.message("file.invalid.name.error", newName));
    }

    getFileSystem().renameFile(requestor, this, newName);
  }

  /**
   * Checks whether this file could be modified. Note that this value may be cached and may differ from
   * write permission of the physical file.
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
   */
  public boolean is(@NotNull VFileProperty property) {
    return false;
  }

  /**
   * <p>Resolves all symbolic links containing in a path to this file and returns a path to a link target (in platform-independent format).</p>
   *
   * <p><b>Note</b>: please use this method judiciously. In most cases VFS clients don't need to resolve links in paths and should
   * work with those provided by a user.</p>
   *
   * @return {@code getPath()} if there are no symbolic links in a file's path;
   * {@code getCanonicalFile().getPath()} if the link was successfully resolved;
   * {@code null} otherwise
   */
  public @Nullable String getCanonicalPath() {
    return getPath();
  }

  /**
   * <p>Resolves all symbolic links containing in a path to this file and returns a link target.</p>
   *
   * <p><b>Note</b>: please use this method judiciously. In most cases VFS clients don't need to resolve links in paths and should
   * work with those provided by a user.</p>
   *
   * @return {@code this} if there are no symbolic links in a file's path;
   *         instance of {@code VirtualFile} if the link was successfully resolved;
   *         {@code null} otherwise
   */
  public @Nullable VirtualFile getCanonicalFile() {
    return this;
  }

  /**
   * Checks whether this {@code VirtualFile} is valid. File can be invalidated either by deleting it or one of its
   * parents with {@link #delete} method or by an external change.
   * If file is not valid only {@link #equals}, {@link #hashCode},
   * {@link #getName()}, {@link #getPath()}, {@link #getUrl()}, {@link #getPresentableUrl()} and methods from
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
   * Gets the child files. The returned files are guaranteed to be valid, if the method is called in a read action.
   *
   * @return array of the child files or {@code null} if this file is not a directory
   * @throws InvalidVirtualFileAccessException if this method is called inside read action on an invalid file
   */
  public abstract VirtualFile[] getChildren();

  /**
   * {@link #getChildren()} is not formally requires the sorting, but many methods rely on stable sorting provided by it
   * But sorting is not cheap, hence this method exists for scenarios there order of children doesn't matter.
   */
  @ApiStatus.Internal
  public VirtualFile @NotNull [] getChildren(boolean requireSorting){
    return getChildren();
  }

  /**
   * Finds child of this file with the given name. The returned file is guaranteed to be valid, if the method is called in a read action.
   *
   * @param name the file name to search by
   * @return the file if found any, {@code null} otherwise
   * @throws InvalidVirtualFileAccessException if this method is called inside read action on an invalid file
   */
  public @Nullable VirtualFile findChild(@NotNull @NonNls String name) {
    VirtualFile[] children = getChildren();
    if (children == null) return null;
    for (VirtualFile child : children) {
      if (child.nameEquals(name)) {
        return child;
      }
    }
    return null;
  }

  public @NotNull VirtualFile findOrCreateChildData(Object requestor, @NotNull @NonNls String name) throws IOException {
    final VirtualFile child = findChild(name);
    if (child != null) return child;
    return createChildData(requestor, name);
  }

  /**
   * Returns the {@link FileType} of this file, or {@link com.intellij.openapi.fileTypes.FileTypes#UNKNOWN} if a type cannot be determined
   * (i.e. file type is not registered via {@link FileTypeRegistry}).
   *
   * <p> Performance notice: this method can be slow. See {@link FileTypeRegistry} javadoc for the details.
   *
   * @see FileTypeRegistry
   */
  public @NotNull FileType getFileType() {
    return FileTypeRegistry.getInstance().getFileTypeByFile(this);
  }

  /**
   * Finds file by path relative to this file.
   *
   * @param relPath the relative path with / used as separators
   * @return the file if found any, {@code null} otherwise
   */
  public @Nullable VirtualFile findFileByRelativePath(@NotNull @NonNls String relPath) {
    VirtualFile child = this;

    int off = CharArrayUtil.shiftForward(relPath, 0, "/");
    while (child != null && off < relPath.length()) {
      int nextOff = relPath.indexOf('/', off);
      if (nextOff < 0) nextOff = relPath.length();
      String name = relPath.substring(off, nextOff);

      if (name.equals("..")) {
        if (child.is(VFileProperty.SYMLINK)) {
          VirtualFile canonicalFile = child.getCanonicalFile();
          child = canonicalFile != null ? canonicalFile.getParent() : null;
        }
        else {
          child = child.getParent();
        }
      }
      else if (!name.equals(".")) {
        child = child.findChild(name);
      }

      off = CharArrayUtil.shiftForward(relPath, nextOff, "/");
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
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull @NonNls String name) throws IOException {
    if (!isDirectory()) {
      throw new IOException(CoreBundle.message("directory.create.wrong.parent.error"));
    }

    if (!isValid()) {
      throw new IOException(CoreBundle.message("invalid.directory.create.files"));
    }

    if (!getFileSystem().isValidName(name)) {
      throw new IOException(CoreBundle.message("directory.invalid.name.error", name));
    }

    if (findChild(name) != null) {
      throw new IOException(CoreBundle.message("file.create.already.exists.error", getUrl(), name));
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
  public @NotNull VirtualFile createChildData(Object requestor, @NotNull @NonNls String name) throws IOException {
    if (!isDirectory()) {
      throw new IOException(CoreBundle.message("file.create.wrong.parent.error"));
    }

    if (!isValid()) {
      throw new IOException(CoreBundle.message("invalid.directory.create.files"));
    }

    if (!getFileSystem().isValidName(name)) {
      throw new IOException(CoreBundle.message("file.invalid.name.error", name));
    }

    if (findChild(name) != null) {
      throw new IOException(CoreBundle.message("file.create.already.exists.error", getUrl(), name));
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
  public void move(final Object requestor, final @NotNull VirtualFile newParent) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(CoreBundle.message("file.move.error", newParent.getPresentableUrl()));
    }

    EncodingRegistry.doActionAndRestoreEncoding(this, () -> {
      getFileSystem().moveFile(requestor, this, newParent);
      return this;
    });
  }

  public @NotNull VirtualFile copy(final Object requestor, final @NotNull VirtualFile newParent, @NotNull @NonNls String copyName) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(CoreBundle.message("file.copy.error", newParent.getPresentableUrl()));
    }

    if (!newParent.isDirectory()) {
      throw new IOException(CoreBundle.message("file.copy.target.must.be.directory"));
    }

    return EncodingRegistry.doActionAndRestoreEncoding(this,
                                                       () -> getFileSystem().copyFile(requestor, this, newParent, copyName));
  }

  /**
   * @return Retrieve the charset file has been loaded with (if loaded) and would be saved with (if would).
   */
  public @NotNull Charset getCharset() {
    Charset charset = getStoredCharset();
    if (charset == null) {
      charset = EncodingRegistry.getInstance().getDefaultCharset();
      setCharset(charset);
    }
    return charset;
  }

  private @Nullable Charset getStoredCharset() {
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

  public void setCharset(Charset charset, @Nullable Runnable whenChanged, boolean fireEventsWhenChanged) {
    Charset oldCharset = getStoredCharset();
    storeCharset(charset);
    if (Comparing.equal(charset, oldCharset)) return;


    byte[] bom = charset == null ? null : CharsetToolkit.getMandatoryBom(charset);
    byte[] existingBOM = getBOM();
    if (bom == null && charset != null && existingBOM != null) {
      bom = CharsetToolkit.canHaveBom(charset, existingBOM) ? existingBOM : null;
    }
    setBOM(bom);

    if (oldCharset != null) { //do not send on detect
      if (whenChanged != null) whenChanged.run();
      if (fireEventsWhenChanged) {
        VirtualFileManager.getInstance().notifyPropertyChanged(this, PROP_ENCODING, oldCharset, charset);
      }
    }
  }

  public boolean isCharsetSet() {
    return getStoredCharset() != null;
  }

  public final void setBinaryContent(byte @NotNull [] content) throws IOException {
    setBinaryContent(content, -1, -1);
  }

  public void setBinaryContent(byte @NotNull [] content, long newModificationStamp, long newTimeStamp) throws IOException {
    setBinaryContent(content, newModificationStamp, newTimeStamp, this);
  }

  /**
   * Sets contents of the virtual file to {@code content}.
   * The BOM, if present, should be included in the {@code content} buffer.
   */
  @RequiresWriteLock(generateAssertion = false)
  public void setBinaryContent(byte @NotNull [] content, long newModificationStamp, long newTimeStamp, Object requestor) throws IOException {
    try (OutputStream outputStream = getOutputStream(requestor, newModificationStamp, newTimeStamp)) {
      outputStream.write(content);
    }
  }

  /**
   * Creates the {@code OutputStream} for this file.
   * Writes BOM first, if there is any. See <a href=http://unicode.org/faq/utf_bom.html>Unicode Byte Order Mark FAQ</a> for an explanation.
   * This method requires WA around it (and all the operations with OutputStream returned, until its closing): currently the indexes pipeline relies on file content being changed under WA
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if {@code requestor} is {@code null}.
   *                  See {@link VirtualFileEvent#getRequestor} and {@link SafeWriteRequestor}.
   * @return {@code OutputStream}
   * @throws IOException if an I/O error occurs
   */
  @RequiresWriteLock(generateAssertion = false)
  public final @NotNull OutputStream getOutputStream(Object requestor) throws IOException {
    return getOutputStream(requestor, -1, -1);
  }

  /**
   * <p>Gets the {@code OutputStream} for this file and sets modification stamp and time stamp to the specified values
   * after closing the stream.</p>
   *
   * <p>Normally, you should not use this method.</p>
   *
   * <p>Writes BOM first, if there is any. See <a href=http://unicode.org/faq/utf_bom.html>Unicode Byte Order Mark FAQ</a> for an explanation.</p>
   *
   * @param requestor            any object to control who called this method. Note that
   *                             it is considered to be an external change if {@code requestor} is {@code null}.
   *                             See {@link VirtualFileEvent#getRequestor} and {@link SafeWriteRequestor}.
   * @param newModificationStamp new modification stamp or -1 if no special value should be set
   * @param newTimeStamp         new time stamp or -1 if no special value should be set
   * @return {@code OutputStream}
   * @throws IOException if an I/O error occurs
   * @see #getModificationStamp()
   */
  @RequiresWriteLock(generateAssertion = false)
  public abstract @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException;

  /**
   * Returns file content as an array of bytes.
   * Has the same effect as {@link #contentsToByteArray(boolean) contentsToByteArray(true)}.
   *
   * @return file content
   * @throws IOException if an I/O error occurs
   * @see #contentsToByteArray(boolean)
   * @see #getInputStream()
   */
  public abstract byte @NotNull [] contentsToByteArray() throws IOException;

  /**
   * Returns file content as an array of bytes, including BOM, if present.
   *
   * @param cacheContent set true to
   * @return file content
   * @throws IOException if an I/O error occurs
   * @see #contentsToByteArray()
   */
  public byte @NotNull [] contentsToByteArray(boolean cacheContent) throws IOException {
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
   * <p>Refreshes the cached file information from the physical file system. If this file is not a directory
   * the timestamp value is refreshed and {@code contentsChanged} event is fired if it is changed.<p>
   * If this file is a directory the set of its children is refreshed. If recursive value is {@code true} all
   * children are refreshed recursively.</p>
   *
   * <p>When invoking synchronous refresh from a thread other than the event dispatch thread, the current thread must
   * NOT be in a read action, otherwise a deadlock may occur.</p>
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
   * after the operation is completed. The runnable is executed on event dispatch thread inside write action.
   */
  public abstract void refresh(boolean asynchronous, boolean recursive, @Nullable Runnable postRunnable);

  public @NotNull @NlsSafe String getPresentableName() {
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
  protected boolean nameEquals(@NotNull @NonNls String name) {
    return Comparing.equal(getNameSequence(), name);
  }

  /**
   * Gets the {@code InputStream} for this file.
   * Skips BOM if there is any. See <a href=http://unicode.org/faq/utf_bom.html>Unicode Byte Order Mark FAQ</a> for an explanation.
   *
   * @return {@code InputStream}
   * @throws IOException if an I/O error occurs
   * @see #contentsToByteArray
   */
  public abstract @NotNull InputStream getInputStream() throws IOException;

  public byte @Nullable [] getBOM() {
    return getUserData(BOM_KEY);
  }

  public void setBOM(byte @Nullable [] BOM) {
    putUserData(BOM_KEY, BOM);
  }

  @Override
  public String toString() {
    return "VirtualFile: " + getPresentableUrl();
  }

  public boolean exists() {
    return isValid();
  }

  /**
   * BEWARE: method name may be quite misleading: the definition of 'local' relies on "being subclass of {@link LocalFileSystem}"
   * This definition leads to quite counterintuitive behavior: the 'file://...' filesystem is 'local' -- which is intuitive.
   * But 'temp://...' i.e., in-memory filesystem is also considered 'local' -- which is not very intuitive.
   * Moreover, archive ('jar://...', 'zip://...', etc.) filesystems are considered NOT local, even if the actual archive
   * is a local file -- also quite counterintuitive.
   * It is too late to change this behavior -- method is very widely used.
   * So the only choice is: constant vigilance while using it.
   *
   * @return true if filesystem inherits {@link LocalFileSystem} (<b>including temporary!</b>)
   */
  public boolean isInLocalFileSystem() {
    return false;
  }

  private static final Key<String> DETECTED_LINE_SEPARATOR_KEY = Key.create("DETECTED_LINE_SEPARATOR_KEY");

  /**
   * @return Line separator for this file.
   * It is always null for directories and binaries, and possibly null if a separator isn't yet known.
   * @see LineSeparator
   */
  public @Nullable @NlsSafe String getDetectedLineSeparator() {
    return getUserData(DETECTED_LINE_SEPARATOR_KEY);
  }

  public void setDetectedLineSeparator(@Nullable String separator) {
    putUserData(DETECTED_LINE_SEPARATOR_KEY, separator);
  }

  public <T> T computeWithPreloadedContentHint(byte @NotNull [] preloadedContentHint, @NotNull Supplier<? extends T> computable) {
    return computable.get();
  }

  /**
   * Returns {@code true} if this file is a symlink that is either <i>recursive</i> (i.e. points to this file' parent) or
   * <i>circular</i> (i.e. its path has a form of "/.../linkX/.../linkX").
   */
  public boolean isRecursiveOrCircularSymlink() {
    if (!is(VFileProperty.SYMLINK)) return false;
    VirtualFile resolved = getCanonicalFile();
    // invalid symlink
    if (resolved == null) return false;
    // if it's recursive
    if (VfsUtilCore.isAncestor(resolved, this, false)) return true;

    // check if it's circular - any symlink above resolves to my target too
    for (VirtualFile p = getParent(); p != null ; p = p.getParent()) {
      if (p.is(VFileProperty.SYMLINK)) {
        VirtualFile parentResolved = p.getCanonicalFile();
        if (resolved.equals(parentResolved)) return true;
      }
    }
    return false;
  }

  /**
   * @return if this directory (or, if this is a file, its parent directory) supports case-sensitive children file names
   * (i.e. treats "README.TXT" and "readme.txt" as different files).
   * Examples of these directories include regular directories on Linux, directories in case-sensitive volumes on Mac and
   * NTFS directories configured with "fsutil.exe file setCaseSensitiveInfo" on Windows 10+.
   */
  @ApiStatus.Experimental
  public boolean isCaseSensitive() {
    return getFileSystem().isCaseSensitive();
  }
}