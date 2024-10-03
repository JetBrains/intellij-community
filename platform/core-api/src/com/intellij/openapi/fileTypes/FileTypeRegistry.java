// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A service for retrieving file types for files.
 * <p>
 * <b>Performance notice.</b> There are different rules of file type matching for a file: matching by file name, by extension,
 * by file content, by custom logic providers and so on. They are all executed by the general methods {@code getFileTypeByFile},
 * thus implying that execution of
 * such methods is as long as the sum of all possible matching checks in the worst case. That includes reading file contents to
 * feed to all {@link FileTypeDetector} instances, checking {@link FileTypeIdentifiableByVirtualFile} and so on. Such actions
 * may lead to considerable slowdowns if called on UI thread, e.g. in
 * {@link com.intellij.openapi.vfs.newvfs.BulkFileListener} implementations.
 * <p>
 * If it is possible and correct to restrict file type matching by particular means (e.g. match only by file name),
 * it is advised to do so, in order to improve the performance of the check, e.g. use
 * {@code FileTypeRegistry.getInstance().getFileTypeByFileName(file.getNameSequence())} instead of {@code file.getFileType()}.
 * Otherwise, consider moving the computation into background, e.g. via {@link com.intellij.openapi.vfs.AsyncFileListener} or
 * {@link com.intellij.openapi.application.ReadAction#nonBlocking}.
 */
public abstract class FileTypeRegistry {
  private static volatile FileTypeRegistry instance;
  static {
    ApplicationManager.registerCleaner(() -> instance = null);
  }

  @ApiStatus.Internal
  public static void setInstanceSupplier(@NotNull Supplier<? extends FileTypeRegistry> supplier, @NotNull Disposable parentDisposable) {
    FileTypeRegistry oldInstance = instance;
    instance = supplier.get();
    Disposer.register(parentDisposable, () -> {
      instance = oldInstance;
    });
  }

  @ApiStatus.Internal
  public static boolean isInstanceSupplierSet() {
    return instance != null;
  }

  public abstract boolean isFileIgnored(@NotNull VirtualFile file);

  /**
   * Checks if the given file has the given file type.
   */
  public boolean isFileOfType(@NotNull VirtualFile file, @NotNull FileType type) {
    return file.getFileType() == type;
  }

  public @Nullable LanguageFileType findFileTypeByLanguage(@NotNull Language language) {
    return language.findMyFileType(getRegisteredFileTypes());
  }

  public static FileTypeRegistry getInstance() {
    FileTypeRegistry cached = instance;
    if (cached == null) {
      // in tests FileTypeManager service maybe not preloaded, so, instance is not set
      Application application = ApplicationManager.getApplication();
      Class<? extends FileTypeRegistry> aClass = null;
      try {
        aClass = (Class<? extends FileTypeRegistry>)Class.forName("com.intellij.openapi.fileTypes.FileTypeManager");
      }
      catch (ClassNotFoundException ignored) {
      }
      instance = cached = application == null || aClass == null || !application.hasComponent(aClass)
                          ? new EmptyFileTypeRegistry() : application.getService(aClass);
    }
    return cached;
  }

  /**
   * Returns the list of all registered file types.
   */
  public abstract FileType @NotNull [] getRegisteredFileTypes();

  /**
   * Returns the file type for the specified file.
   */
  public abstract @NotNull FileType getFileTypeByFile(@NotNull VirtualFile file);

  /**
   * Returns the file type for the specified file.
   *
   * @param content a content of the file (if already available, to avoid reading from disk again)
   */
  public @NotNull FileType getFileTypeByFile(@NotNull VirtualFile file, byte @Nullable [] content) {
    return getFileTypeByFile(file);
  }

  /**
   * Returns the file type for the specified file name, or {@link FileTypes#UNKNOWN} if not found.
   */
  public @NotNull FileType getFileTypeByFileName(@NotNull CharSequence fileNameSeq) {
    return getFileTypeByFileName(fileNameSeq.toString());
  }

  /**
   * Same as {@linkplain FileTypeRegistry#getFileTypeByFileName(CharSequence)} but receives String parameter.
   * Consider using the method above in case when you want to get VirtualFile's file type by file name.
   */
  public abstract @NotNull FileType getFileTypeByFileName(@NotNull String fileName);

  /**
   * Returns the file type for the specified extension.
   * Note that a more general way of obtaining file type is with {@link #getFileTypeByFile(VirtualFile)}
   *
   * @param extension The extension for which the file type is requested, not including the leading '.'.
   * @return The file type instance, or {@link UnknownFileType#INSTANCE} if corresponding file type not found
   */
  public abstract @NotNull FileType getFileTypeByExtension(@NotNull String extension);

  /**
   * Finds a file type with the specified name.
   */
  public abstract FileType findFileTypeByName(@NotNull String fileTypeName);

  /**
   * Pluggable file type detector by content
   */
  public interface FileTypeDetector {
    ExtensionPointName<FileTypeDetector> EP_NAME = new ExtensionPointName<>("com.intellij.fileTypeDetector");

    /**
     * Detects file type by its (possibly binary) content on disk. Your detector must be as light as possible.
     * In particular, it must not perform any heavy processing, e.g. PSI access, indices, documents, etc.
     * The detector must refrain from throwing exceptions (including pervasive {@link com.intellij.openapi.progress.ProcessCanceledException})
     * @param file to analyze
     * @param firstBytes of the file for identifying its file type
     * @param firstCharsIfText - characters converted from {@code firstBytes} parameter if the file content was determined to be text, {@code null} otherwise
     * @return detected file type, or null if was unable to detect
     */
    @Nullable FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText);

    /**
     * Defines how much content is required for this detector to detect file type reliably. At least such amount of bytes
     * will be passed to {@link #detect(VirtualFile, ByteSequence, CharSequence)} if present.
     *
     * @return number of first bytes to be given
     */
    default int getDesiredContentPrefixLength() {
      return 1024;
    }

    /** @deprecated not used anymore, do not override */
    @Deprecated
    @ApiStatus.ScheduledForRemoval
    default int getVersion() { return 0; }
  }
}
