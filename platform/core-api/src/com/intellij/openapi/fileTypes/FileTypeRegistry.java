/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class FileTypeRegistry {
  public static Getter<FileTypeRegistry> ourInstanceGetter;

  public abstract boolean isFileIgnored(@NotNull VirtualFile file);

  public static FileTypeRegistry getInstance() {
    return ourInstanceGetter.get();
  }

  /**
   * Returns the list of all registered file types.
   *
   * @return The list of file types.
   */
  public abstract FileType[] getRegisteredFileTypes();

  /**
   * Returns the file type for the specified file.
   *
   * @param file The file for which the type is requested.
   * @return The file type instance.
   */
  @NotNull
  public abstract FileType getFileTypeByFile(@NotNull VirtualFile file);

  /**
   * Returns the file type for the specified file name.
   *
   * @param fileName The file name for which the type is requested.
   * @return The file type instance, or {@link FileTypes#UNKNOWN} if not found.
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
   * Tries to detect whether the file is text or not by analyzing its content.
   * @param file to analyze
   * @return {@link com.intellij.openapi.fileTypes.PlainTextFileType} if file looks like text,
   *          or another file type if some file type detector identified the file
   *          or the {@link UnknownFileType} if file is binary or we are unable to detect.
   * @deprecated use {@link VirtualFile#getFileType()} instead
   */
  @NotNull
  @Deprecated
  public abstract FileType detectFileTypeFromContent(@NotNull VirtualFile file);

  /**
   * Finds a file type with the specified name.
   */
  @Nullable
  public abstract FileType findFileTypeByName(@NotNull String fileTypeName);

  /**
   * Pluggable file type detector by content
   */
  public interface FileTypeDetector {
    ExtensionPointName<FileTypeDetector> EP_NAME = ExtensionPointName.create("com.intellij.fileTypeDetector");
    /**
     * Detects file type by its content
     * @param file to analyze
     * @param firstBytes of the file for identifying its file type
     * @param firstCharsIfText - characters, converted from first bytes parameter if the file content was determined to be text, or null otherwise
     * @return detected file type, or null if was unable to detect
     */
    @Nullable
    FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText);

    int getVersion();
  }
}
