/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.search;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class FileTypeIndex extends ScalarIndexExtension<FileType>
  implements FileBasedIndex.InputFilter, KeyDescriptor<FileType>, DataIndexer<FileType, Void, FileContent> {
  private static final EnumeratorStringDescriptor ENUMERATOR_STRING_DESCRIPTOR = new EnumeratorStringDescriptor();

  @NotNull
  public static Collection<VirtualFile> getFiles(@NotNull FileType fileType, @NotNull GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, fileType, scope);
  }

  public static final ID<FileType, Void> NAME = ID.create("filetypes");

  private final FileTypeRegistry myFileTypeManager;

  public FileTypeIndex(FileTypeRegistry fileTypeRegistry) {
    myFileTypeManager = fileTypeRegistry;
  }

  @NotNull
  @Override
  public ID<FileType, Void> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<FileType, Void, FileContent> getIndexer() {
    return this;
  }

  @NotNull
  @Override
  public KeyDescriptor<FileType> getKeyDescriptor() {
    return this;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return this;
  }

  @Override
  public boolean dependsOnFileContent() {
    return false;
  }

  @Override
  public int getVersion() {
    FileType[] types = myFileTypeManager.getRegisteredFileTypes();
    int version = 2;
    for (FileType type : types) {
      version += type.getName().hashCode();
    }

    version *= 31;
    for (FileTypeRegistry.FileTypeDetector detector : Extensions.getExtensions(FileTypeRegistry.FileTypeDetector.EP_NAME)) {
      version += detector.getVersion();
    }
    return version;
  }

  @Override
  public boolean acceptInput(@NotNull VirtualFile file) {
    return !file.isDirectory();
  }

  @Override
  public void save(@NotNull DataOutput out, FileType value) throws IOException {
    ENUMERATOR_STRING_DESCRIPTOR.save(out, value.getName());
  }

  @Override
  public FileType read(@NotNull DataInput in) throws IOException {
    String read = ENUMERATOR_STRING_DESCRIPTOR.read(in);
    return myFileTypeManager.findFileTypeByName(read);
  }

  @Override
  public int getHashCode(FileType value) {
    return value.getName().hashCode();
  }

  @Override
  public boolean isEqual(FileType val1, FileType val2) {
    return Comparing.equal(val1, val2);
  }

  @NotNull
  @Override
  public Map<FileType, Void> map(@NotNull FileContent inputData) {
    return Collections.singletonMap(inputData.getFileType(), null);
  }

  public static boolean containsFileOfType(@NotNull FileType type, @NotNull GlobalSearchScope scope) {
    return !FileBasedIndex.getInstance().processValues(NAME, type, null, new FileBasedIndex.ValueProcessor<Void>() {
      @Override
      public boolean process(VirtualFile file, Void value) {
        return false;
      }
    }, scope);
  }
}
