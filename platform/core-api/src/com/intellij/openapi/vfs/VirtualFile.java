// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.core.CoreBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayFactory;
import com.intellij.util.LineSeparator;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.text.CharArrayUtil;
import kotlin.coroutines.Continuation;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/// Represents a file in [VirtualFileSystem]. A particular file is represented by equal
/// `VirtualFile` instances for the entire lifetime of the IDE process, unless the file
/// is deleted, in which case [#isValid()] will return `false`.
///
/// VirtualFile instances are created on request, so there can be several instances corresponding to the same file.
/// All of them are equal, have the same `hashCode` and use shared storage for all related data, including user data
/// (see [UserDataHolder]).
///
/// If an in-memory implementation of VirtualFile is required, [LightVirtualFile] can be used.
///
/// VirtualFile is also a [ModificationTracker] whose stamp is incremented whenever the file's content changes.
///
/// Please see [Virtual File System](https://plugins.jetbrains.com/docs/intellij/virtual-file-system.html)
/// for a high-level overview.
///
/// @see VirtualFileSystem
/// @see VirtualFileManager
/// @see com.intellij.openapi.vfs.VfsUtil
@SuppressWarnings("SplitModeApiUsage")
public abstract class VirtualFile extends UserDataHolderBase implements ModificationTracker {
  public static final VirtualFile[] EMPTY_ARRAY = new VirtualFile[0];
  public static final ArrayFactory<VirtualFile> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new VirtualFile[count];

  /// Used as a property name in the [VirtualFilePropertyEvent] fired when the name of a [VirtualFile] changes.
  ///
  /// @see VirtualFileListener#propertyChanged
  /// @see VirtualFilePropertyEvent#getPropertyName
  public static final String PROP_NAME = "name";

  /// Used as a property name in the [VirtualFilePropertyEvent] fired when the encoding of a [VirtualFile] changes.
  ///
  /// @see VirtualFileListener#propertyChanged
  /// @see VirtualFilePropertyEvent#getPropertyName
  public static final String PROP_ENCODING = "encoding";

  /// Used as a property name in the [VirtualFilePropertyEvent] fired when write permission of a [VirtualFile] changes.
  ///
  /// @see VirtualFileListener#propertyChanged
  /// @see VirtualFilePropertyEvent#getPropertyName
  public static final String PROP_WRITABLE = "writable";

  /// Used as a property name in the [VirtualFilePropertyEvent] fired when a visibility of a [VirtualFile] changes.
  ///
  /// @see VirtualFileListener#propertyChanged
  /// @see VirtualFilePropertyEvent#getPropertyName
  public static final String PROP_HIDDEN = "HIDDEN";

  /// Used as a property name in the [VirtualFilePropertyEvent] fired when a symlink target of a [VirtualFile] changes.
  ///
  /// @see VirtualFileListener#propertyChanged
  /// @see VirtualFilePropertyEvent#getPropertyName
  public static final String PROP_SYMLINK_TARGET = "symlink";

  /// Used as a property name in the [VirtualFilePropertyEvent] fired when a case-sensitivity of a [VirtualFile] children has changed or became available.
  /// After this event the [VirtualFile#isCaseSensitive()] may return different value.
  ///
  /// @see VirtualFileListener#propertyChanged
  /// @see VirtualFilePropertyEvent#getPropertyName
  public static final String PROP_CHILDREN_CASE_SENSITIVITY = "CHILDREN_CASE_SENSITIVITY";

  /// Acceptable values for "propertyName" argument of [`VFilePropertyChangeEvent()`][VFilePropertyChangeEvent#VFilePropertyChangeEvent].
  @MagicConstant(stringValues = {PROP_NAME, PROP_ENCODING, PROP_HIDDEN, PROP_WRITABLE, PROP_SYMLINK_TARGET, PROP_CHILDREN_CASE_SENSITIVITY})
  public @interface PropName {}

  private static final Logger LOG = Logger.getInstance(VirtualFile.class);
  private static final Key<byte[]> BOM_KEY = Key.create("BOM");
  private static final Key<Charset> CHARSET_KEY = Key.create("CHARSET");

  protected VirtualFile() {
    if (this instanceof Disposable) {
      throw new IllegalStateException(
        "VirtualFile must not implement Disposable because of life-cycle requirements. " +
        "E.g. VirtualFile should exist throughout the application and may not be disposed half-way."
      );
    }
  }

  /// Returns a name of this file.
  ///
  /// **Performance note:** the operation is not necessarily cheap; the main implementation doesn't retain the name string
  /// (to reduce memory usage) and may reach an index to fetch it.
  /// Avoid its use in bulk operations – e.g., use methods from [com.intellij.openapi.vfs.newvfs.events.VFileEvent] instead.
  public abstract @NotNull @NlsSafe String getName();

  /// @see #getName the performance note
  public @NotNull @NlsSafe CharSequence getNameSequence() {
    return getName();
  }

  /// Returns the [VirtualFileSystem] this file belongs to.
  public abstract @NotNull VirtualFileSystem getFileSystem();

  /// Gets the path of this file. Path is a string that uniquely identifies a file within a given [VirtualFileSystem].
  /// Format of the path depends on the concrete file system.
  /// For [LocalFileSystem] it is an absolute file path with file separator characters (`File#separatorChar`)
  /// replaced with the forward slash (`'/'`).
  /// If you need to show the path in UI, use [#getPresentableUrl()] instead.
  ///
  /// @see #getName the performance note
  /// @see #toNioPath()
  public abstract @NotNull String getPath();

  /// Returns a related [Path] for a given virtual file where possible, otherwise an exception is thrown.
  /// The returned [Path] may not have a default filesystem behind.
  ///
  /// Use [#getFileSystem()] and [VirtualFileSystem#getNioPath(VirtualFile)] to avoid the exception
  ///
  /// @throws UnsupportedOperationException if this VirtualFile does not have an associated [Path]
  public @NotNull Path toNioPath() {
    Path path = getFileSystem().getNioPath(this);
    if (path == null) {
      throw new UnsupportedOperationException("Failed to map " + this + " (filesystem " + getFileSystem() + ") into nio Path");
    }
    return path;
  }

  /// Returns the URL of this file. The URL is a string that uniquely identifies a file in all file systems.
  /// It has the following format: `<protocol>://<path>`.
  ///
  /// File can be found by its URL using [VirtualFileManager#findFileByUrl] method.
  ///
  /// Please note these URLs are intended for use withing VFS - meaning they are not necessarily RFC-compliant.
  /// Besides, it's better not to show them in UI; use [#getPresentableUrl()] for that.
  ///
  /// @see #getName the performance note
  /// @see VirtualFileManager#findFileByUrl
  /// @see VirtualFile#getPath
  /// @see VirtualFileSystem#getProtocol
  public @NotNull String getUrl() {
    return VirtualFileManager.constructUrl(getFileSystem().getProtocol(), getPath());
  }

  /// Returns a "presentable URL" of this file. "Presentable URL" is a string to be used for displaying this file in the UI.
  ///
  /// @see #getName the performance note
  /// @see VirtualFileSystem#extractPresentableUrl
  @ApiStatus.NonExtendable
  public @NotNull @NlsSafe String getPresentableUrl() {
    return getFileSystem().extractPresentableUrl(getPath());
  }

  /// Returns the file extension or `null`.
  ///
  /// @see #getName the performance note
  public @Nullable @NlsSafe String getExtension() {
    CharSequence extension = FileUtilRt.getExtension(getNameSequence(), null);
    return extension == null ? null : extension.toString();
  }

  /// Returns the file name without an extension.
  ///
  /// @see #getName the performance note
  public @NlsSafe @NotNull String getNameWithoutExtension() {
    return FileUtilRt.getNameWithoutExtension(getNameSequence()).toString();
  }

  /// Renames this file to the `newName`.
  ///
  /// This method should only be called within [`write action`][Application#runWriteAction(Runnable)].
  ///
  /// @param requestor any object to control who called this method. Note that
  ///                  it is considered to be an external change if `requestor` is `null`.
  ///                  See [VirtualFileEvent#getRequestor]
  /// @param newName   the new file name
  /// @throws IOException if file failed to be renamed
  @RequiresWriteLock
  public void rename(Object requestor, @NotNull String newName) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (getName().equals(newName)) return;
    if (!getFileSystem().isValidName(newName)) {
      throw new IOException(CoreBundle.message("file.invalid.name.error", newName));
    }

    getFileSystem().renameFile(requestor, this, newName);
  }

  /// Checks whether this file could be modified. Note that this value may be cached and may differ from
  /// write permission of the physical file.
  ///
  /// @return `true` if this file is writable, `false` otherwise
  public abstract boolean isWritable();

  public void setWritable(boolean writable) throws IOException {
    throw new IOException("Not supported");
  }

  /// Checks whether this file is a directory.
  ///
  /// @return `true` if this file is a directory, `false` otherwise
  public abstract boolean isDirectory();

  /// Checks whether this file has a specific property.
  ///
  /// @return `true` if the file has a specific property, `false` otherwise
  public boolean is(@NotNull VFileProperty property) {
    return false;
  }

  /// Resolves all symbolic links containing in a path to this file and returns a path to a link target (in platform-independent format).
  ///
  /// **Note**: please use this method judiciously. In most cases VFS clients don't need to resolve links in paths and should
  /// work with those provided by a user.
  ///
  /// @return `getPath()` if there are no symbolic links in a file's path;
  /// `getCanonicalFile().getPath()` if the link was successfully resolved;
  /// `null` otherwise
  public @Nullable String getCanonicalPath() {
    return getPath();
  }

  /// Resolves all symbolic links containing in a path to this file and returns a link target.
  ///
  /// **Note**: please use this method judiciously. In most cases VFS clients don't need to resolve links in paths and should
  /// work with those provided by a user.
  ///
  /// @return `this` if there are no symbolic links in a file's path;
  ///         instance of `VirtualFile` if the link was successfully resolved;
  ///         `null` otherwise
  public @Nullable VirtualFile getCanonicalFile() {
    return this;
  }

  /// Checks whether this `VirtualFile` is valid. File can be invalidated either by deleting it or one of its
  /// parents with [#delete] method or by an external change.
  /// If file is not valid only [#equals], [#hashCode],
  /// [#getName()], [#getPath()], [#getUrl()], [#getPresentableUrl()] and methods from
  /// [UserDataHolder] can be called for it. Using any other methods for an invalid [VirtualFile] instance
  /// produce unpredictable results.
  ///
  /// @return `true` if this is a valid file, `false` otherwise
  public abstract boolean isValid();

  /// Gets the parent `VirtualFile`.
  ///
  /// @return the parent file or `null` if this file is a root directory
  public abstract VirtualFile getParent();

  /// Gets the child files.
  /// The returned files are guaranteed to be valid if the method is called in a read action.
  ///
  /// @return array of the child files.
  ///         If the file is not [#isDirectory()], the method could return either `null`, or an empty array.
  ///         New implementations should prefer an empty array, but `null` is still legit for backward compatibility.
  /// @throws InvalidVirtualFileAccessException if this method is called inside read action on an invalid file
  public abstract VirtualFile /*@Nullable*/ [] getChildren();

  /// While [#getChildren()] is not formally required to return a sorted result, still many use-cases \_rely\_ on stable sorting
  /// provided by it. But the sorting is not cheap; hence this method exists for scenarios there order of children doesn't matter,
  /// for implementations that may skip it.
  @ApiStatus.Internal
  public VirtualFile @Nullable [] getChildren(boolean requireSorting){
    return getChildren();
  }

  /// Finds child of this file with the given name. The returned file is guaranteed to be valid, if the method is called in a read action.
  ///
  /// @param name the file name to search by
  /// @return the file if found any, `null` otherwise
  /// @throws InvalidVirtualFileAccessException if this method is called inside read action on an invalid file
  public @Nullable VirtualFile findChild(@NotNull String name) {
    VirtualFile[] children = getChildren();
    if (children == null) return null;
    for (VirtualFile child : children) {
      if (child.nameEquals(name)) {
        return child;
      }
    }
    return null;
  }

  public @NotNull VirtualFile findOrCreateChildData(Object requestor, @NotNull String name) throws IOException {
    final VirtualFile child = findChild(name);
    if (child != null) return child;
    return createChildData(requestor, name);
  }

  /// Returns the [FileType] of this file, or [com.intellij.openapi.fileTypes.FileTypes#UNKNOWN] if a type cannot be determined
  /// (i.e. file type is not registered via [FileTypeRegistry]).
  ///
  /// Performance notice: this method can be slow. See [FileTypeRegistry] javadoc for the details.
  ///
  /// @see FileTypeRegistry
  public @NotNull FileType getFileType() {
    return FileTypeRegistry.getInstance().getFileTypeByFile(this);
  }

  /// Finds file by path relative to this file.
  ///
  /// @param relPath the relative path with / used as separators
  /// @return the file if found any, `null` otherwise
  public @Nullable VirtualFile findFileByRelativePath(@NotNull String relPath) {
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

  /// Creates a subdirectory in this directory. This method should be only called within write-action.
  /// See [Application#runWriteAction].
  ///
  /// @param requestor any object to control who called this method. Note that
  ///                  it is considered to be an external change if `requestor` is `null`.
  ///                  See [VirtualFileEvent#getRequestor]
  /// @param name      directory name
  /// @return `VirtualFile` representing the created directory
  /// @throws IOException if directory failed to be created
  @RequiresWriteLock
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull String name) throws IOException {
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

  /// Creates a new file in this directory. This method should be only called within write-action.
  /// See [Application#runWriteAction].
  ///
  /// @param requestor any object to control who called this method. Note that
  ///                  it is considered to be an external change if `requestor` is `null`.
  ///                  See [VirtualFileEvent#getRequestor]
  /// @return `VirtualFile` representing the created file
  /// @throws IOException if file failed to be created
  @RequiresWriteLock
  public @NotNull VirtualFile createChildData(Object requestor, @NotNull String name) throws IOException {
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

  /// Deletes this file. This method should be only called within write-action.
  /// See [Application#runWriteAction].
  ///
  /// @param requestor any object to control who called this method. Note that
  ///                  it is considered to be an external change if `requestor` is `null`.
  ///                  See [VirtualFileEvent#getRequestor]
  /// @throws IOException if file failed to be deleted
  @RequiresWriteLock
  public void delete(Object requestor) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    LOG.assertTrue(isValid(), "Deleting invalid file");
    getFileSystem().deleteFile(requestor, this);
  }

  /// Moves this file to another directory. This method should be only called within write-action.
  /// See [Application#runWriteAction].
  ///
  /// @param requestor any object to control who called this method. Note that
  ///                  it is considered to be an external change if `requestor` is `null`.
  ///                  See [VirtualFileEvent#getRequestor]
  /// @param newParent the directory to move this file to
  /// @throws IOException if file failed to be moved
  @RequiresWriteLock
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

  @RequiresWriteLock
  public @NotNull VirtualFile copy(final Object requestor, final @NotNull VirtualFile newParent, @NotNull String copyName) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(CoreBundle.message("file.copy.error", newParent.getPresentableUrl()));
    }

    if (!newParent.isDirectory()) {
      throw new IOException(CoreBundle.message("file.copy.target.must.be.directory"));
    }

    return EncodingRegistry.doActionAndRestoreEncoding(this,
                                                       () -> getFileSystem().copyFile(requestor, this, newParent, copyName));
  }

  /// @return Retrieve the charset file has been loaded with (if loaded) and would be saved with (if would).
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

  /// Sets contents of the virtual file to `content`.
  /// The BOM, if present, should be included in the `content` buffer.
  @RequiresWriteLock(generateAssertion = false)
  public void setBinaryContent(byte @NotNull [] content, long newModificationStamp, long newTimeStamp, Object requestor) throws IOException {
    try (OutputStream outputStream = getOutputStream(requestor, newModificationStamp, newTimeStamp)) {
      outputStream.write(content);
    }
  }

  /// Creates the `OutputStream` for this file.
  /// Writes BOM first, if there is any. See [Unicode Byte Order Mark FAQ](http://unicode.org/faq/utf_bom.html) for an explanation.
  /// This method requires WA around it (and all the operations with OutputStream returned, until its closing): currently the indexes pipeline relies on file content being changed under WA
  /// @param requestor any object to control who called this method. Note that
  ///                  it is considered to be an external change if `requestor` is `null`.
  ///                  See [VirtualFileEvent#getRequestor] and [SafeWriteRequestor].
  /// @throws IOException if an I/O error occurs
  @RequiresWriteLock(generateAssertion = false)
  public final @NotNull OutputStream getOutputStream(Object requestor) throws IOException {
    return getOutputStream(requestor, -1, -1);
  }

  /// Gets the `OutputStream` for this file and sets modification stamp and time stamp to the specified values
  /// after closing the stream.
  ///
  /// Normally, you should not use this method.
  ///
  /// Writes BOM first, if there is any. See [Unicode Byte Order Mark FAQ](http://unicode.org/faq/utf_bom.html) for an explanation.
  ///
  /// @param requestor            any object to control who called this method. Note that
  ///                             it is considered to be an external change if `requestor` is `null`.
  ///                             See [VirtualFileEvent#getRequestor] and [SafeWriteRequestor].
  /// @param newModificationStamp new modification stamp or -1 if no special value should be set
  /// @param newTimeStamp         new time stamp or -1 if no special value should be set
  /// @throws IOException if an I/O error occurs
  /// @see #getModificationStamp()
  @RequiresWriteLock(generateAssertion = false)
  public abstract @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException;

  /// Returns file content as an array of bytes.
  /// Has the same effect as [`contentsToByteArray(true)`][#contentsToByteArray(boolean)].
  ///
  /// @throws IOException if an I/O error occurs
  /// @see #contentsToByteArray(boolean)
  /// @see #getInputStream()
  public abstract byte @NotNull [] contentsToByteArray() throws IOException;

  /// Returns file content as an array of bytes, including BOM, if present.
  ///
  /// @param cacheContent set true to
  /// @return file content
  /// @throws IOException if an I/O error occurs
  /// @see #contentsToByteArray()
  public byte @NotNull [] contentsToByteArray(boolean cacheContent) throws IOException {
    return contentsToByteArray();
  }

  /// Gets modification stamp value. Modification stamp is a value changed by any modification
  /// of the content of the file. Note that it is not related to the file modification time.
  ///
  /// @return modification stamp
  /// @see #getTimeStamp()
  public long getModificationStamp() {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /// Gets the timestamp for this file. Note that this value may be cached and may differ from
  /// the timestamp of the physical file.
  ///
  /// @return timestamp
  /// @see java.nio.file.Files#getLastModifiedTime
  public abstract long getTimeStamp();

  /// File length in bytes.
  ///
  /// @return the length of this file.
  public abstract long getLength();

  /// Refreshes the cached file information from the physical file system. If this file is not a directory
  /// the timestamp value is refreshed and `contentsChanged` event is fired if it is changed.
  ///
  /// If this file is a directory the set of its children is refreshed. If recursive value is `true` all
  /// children are refreshed recursively.
  ///
  /// When invoking synchronous refresh from a thread other than the event dispatch thread, the current thread must
  /// NOT be in a read action, otherwise a deadlock may occur.
  ///
  /// For suspend function of VFS refresh, use [com.intellij.openapi.vfs.newvfs.RefreshQueue#refresh(boolean, List, Continuation)]
  ///
  /// @param asynchronous if `true`, the method will return immediately and the refresh will be processed
  ///                     in the background. If `false`, the method will return only after the refresh
  ///                     is done and the VFS change events caused by the refresh have been fired and processed
  ///                     in the event dispatch thread. Instead of synchronous refreshes, it's recommended to use
  ///                     asynchronous refreshes with a `postRunnable` whenever possible.
  /// @param recursive    whether to refresh all the files in this directory recursively
  public void refresh(boolean asynchronous, boolean recursive) {
    refresh(asynchronous, recursive, null);
  }

  /// The same as [#refresh(boolean, boolean)] but also runs `postRunnable`
  /// after the operation is completed. The runnable is executed on event dispatch thread inside write action.
  public abstract void refresh(boolean asynchronous, boolean recursive, @Nullable Runnable postRunnable);

  public @NotNull @NlsSafe String getPresentableName() {
    return getName();
  }

  @Override
  public long getModificationCount() {
    return isValid() ? getTimeStamp() : -1;
  }

  /// @return whether file name equals to this name
  ///         result depends on the filesystem specifics
  protected boolean nameEquals(@NotNull String name) {
    return Comparing.equal(getNameSequence(), name);
  }

  /// Gets the `InputStream` for this file.
  /// Skips BOM if there is any. See [Unicode Byte Order Mark FAQ](http://unicode.org/faq/utf_bom.html) for an explanation.
  ///
  /// @throws IOException if an I/O error occurs
  /// @see #contentsToByteArray
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

  /// BEWARE: method name may be quite misleading: the definition of 'local' relies on "being subclass of [LocalFileSystem]"
  /// This definition leads to quite counterintuitive behavior: the 'file://...' filesystem is 'local' -- which is intuitive.
  /// But 'temp://...' i.e., in-memory filesystem is also considered 'local' -- which is not very intuitive.
  /// Moreover, archive ('jar://...', 'zip://...', etc.) filesystems are considered NOT local, even if the actual archive
  /// is a local file -- also quite counterintuitive.
  /// It is too late to change this behavior -- method is very widely used.
  /// So the only choice is: constant vigilance while using it.
  ///
  /// @return true if filesystem inherits [LocalFileSystem] (**including temporary!**)
  public boolean isInLocalFileSystem() {
    return false;
  }

  private static final Key<String> DETECTED_LINE_SEPARATOR_KEY = Key.create("DETECTED_LINE_SEPARATOR_KEY");

  /// @return Line separator for this file.
  /// It is always null for directories and binaries, and possibly null if a separator isn't yet known.
  /// @see LineSeparator
  public @Nullable @NlsSafe String getDetectedLineSeparator() {
    return getUserData(DETECTED_LINE_SEPARATOR_KEY);
  }

  public void setDetectedLineSeparator(@Nullable String separator) {
    putUserData(DETECTED_LINE_SEPARATOR_KEY, separator);
  }

  public <T> T computeWithPreloadedContentHint(byte @NotNull [] preloadedContentHint, @NotNull Supplier<? extends T> computable) {
    return computable.get();
  }

  /// Returns `true` if this file is a symlink that is either _recursive_ (i.e., points to this file's parent)
  /// or _circular_ (i.e., its path has a form of "/.../linkX/.../linkX").
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

  /// @return if this directory (or, if this is a file, its parent directory) supports case-sensitive children file names
  /// (i.e. treats "README.TXT" and "readme.txt" as different files).
  /// Examples of these directories include regular directories on Linux, directories in case-sensitive volumes on Mac and
  /// NTFS directories configured with "fsutil.exe file setCaseSensitiveInfo" on Windows 10+.
  @ApiStatus.Experimental
  public boolean isCaseSensitive() {
    return getFileSystem().isCaseSensitive();
  }
}
