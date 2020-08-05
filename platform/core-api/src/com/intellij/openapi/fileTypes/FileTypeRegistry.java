// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * A service for retrieving file types for files.
 *
 * <p><b>Performance notice.</b> There are different rules of file type matching for a file: matching by file name, by extension,
 * by file content, by custom logic providers and so on. They are all executed by the general methods {@code getFileTypeByFile},
 * thus implying that execution of
 * such methods is as long as the sum of all possible matching checks in the worst case. That includes reading file contents to
 * feed to all {@link FileTypeDetector} instances, checking {@link FileTypeIdentifiableByVirtualFile} and so on. Such actions
 * may lead to considerable slowdowns if called on UI thread, e.g. in
 * {@link com.intellij.openapi.vfs.newvfs.BulkFileListener} implementations.
 *
 * <p> If it is possible and correct to restrict file type matching by particular means (e.g. match only by file name),
 * it is advised to do so, in order to improve the performance of the check, e.g. use
 * <pre>{@code FileTypeRegistry.getInstance().getFileTypeByFileName(file.getNameSequence())}</pre>
 * instead of
 * <pre>{@code file.getFileType()}</pre>.
 * Otherwise consider moving the computation into background, e.g. via {@link com.intellij.openapi.vfs.AsyncFileListener} or
 * {@link com.intellij.openapi.application.ReadAction#nonBlocking}.
 *
 * @author yole
 */
public abstract class FileTypeRegistry {
  public static Getter<FileTypeRegistry> ourInstanceGetter;

  public abstract boolean isFileIgnored(@NotNull VirtualFile file);

  /**
   * Checks if the given file has the given file type.
   */
  public boolean isFileOfType(@NotNull VirtualFile file, @NotNull FileType type) {
    FileType actualType = file.getFileType();
    //todo remove scratch check after IDEA-228078 is fixed
    return actualType == type || "Scratch".equals(actualType.getName()) && type == getFileTypeByFileName(file.getNameSequence());
  }

  @Nullable
  public LanguageFileType findFileTypeByLanguage(@NotNull Language language) {
    return language.findMyFileType(getRegisteredFileTypes());
  }

  public static FileTypeRegistry getInstance() {
    if (ourInstanceGetter == null) {
      // in tests FileTypeManager service maybe not preloaded, so, ourInstanceGetter is not set
      return (FileTypeRegistry)ApplicationManager.getApplication().getPicoContainer().getComponentInstance("com.intellij.openapi.fileTypes.FileTypeManager");
    }
    return ourInstanceGetter.get();
  }

  /**
   * Returns the list of all registered file types.
   *
   * @return The list of file types.
   */
  public abstract FileType @NotNull [] getRegisteredFileTypes();

  /**
   * Returns the file type for the specified file.
   *
   * @param file The file for which the type is requested.
   * @return The file type instance.
   */
  @NotNull
  public abstract FileType getFileTypeByFile(@NotNull VirtualFile file);

  /**
   * Returns the file type for the specified file.
   *
   * @param file The file for which the type is requested.
   * @param content Content of the file (if already available, to avoid reading from disk again)
   * @return The file type instance.
   */
  @NotNull
  public FileType getFileTypeByFile(@NotNull VirtualFile file, byte @Nullable [] content) {
    return getFileTypeByFile(file);
  }

  /**
   * Returns the file type for the specified file name.
   *
   * @param fileNameSeq The file name for which the type is requested.
   * @return The file type instance, or {@link FileTypes#UNKNOWN} if not found.
   */
  @NotNull
  public FileType getFileTypeByFileName(@NotNull @NonNls CharSequence fileNameSeq) {
    return getFileTypeByFileName(fileNameSeq.toString());
  }

  /**
   * Same as {@linkplain FileTypeRegistry#getFileTypeByFileName(CharSequence)} but receives String parameter.
   *
   * Consider to use the method above in case when you want to get VirtualFile's file type by file name.
   */
  @NotNull
  public abstract FileType getFileTypeByFileName(@NotNull @NonNls String fileName);

  /**
   * Returns the file type for the specified extension.
   * Note that a more general way of obtaining file type is with {@link #getFileTypeByFile(VirtualFile)}
   *
   * @param extension The extension for which the file type is requested, not including the leading '.'.
   * @return The file type instance, or {@link UnknownFileType#INSTANCE} if corresponding file type not found
   */
  @NotNull
  public abstract FileType getFileTypeByExtension(@NonNls @NotNull String extension);

  /**
   * Finds a file type with the specified name.
   */
  @Nullable
  public abstract FileType findFileTypeByName(@NonNls @NotNull String fileTypeName);

  /**
   * Pluggable file type detector by content
   */
  public interface FileTypeDetector {
    ExtensionPointName<FileTypeDetector> EP_NAME = new ExtensionPointName<>("com.intellij.fileTypeDetector");
    /**
     * Detects file type by its (may be binary) content on disk.
     * Your detector must be as light as possible.
     * In particular, it must not perform any heavy processing, e.g. PSI access, indices, Documents etc.
     * The detector must refrain from throwing exceptions (including pervasive {@link com.intellij.openapi.progress.ProcessCanceledException})
     * @param file to analyze
     * @param firstBytes of the file for identifying its file type
     * @param firstCharsIfText - characters, converted from first bytes parameter if the file content was determined to be text, or null otherwise
     * @return detected file type, or null if was unable to detect
     */
    @Nullable
    FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText);

    /**
     * Returns the file type that this detector is capable of detecting, or null if it can detect
     * multiple file types.
     *
     * @deprecated unused
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
    @Nullable
    default Collection<? extends FileType> getDetectedFileTypes() {
      return null;
    }

    /**
     * Defines how much content is required for this detector to detect file type reliably. At least such amount of bytes
     * will be passed to {@link #detect(VirtualFile, ByteSequence, CharSequence)} if present.
     *
     * @return number of first bytes to be given
     */
    default int getDesiredContentPrefixLength() {
      return 1024;
    }

    /**
     * @deprecated Do not use
     */
    @Deprecated
    default int getVersion() { return 0; }
  }
}
