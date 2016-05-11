/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.stubs;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IndexInfrastructure;
import com.intellij.util.indexing.IndexingStamp;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TLongObjectHashMap;
import gnu.trove.TObjectLongHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

class StubVersionMap {
  private static final String INDEXED_FILETYPES = "indexed_filetypes";
  private static final String RECORD_SEPARATOR = "\uFFFF";
  private static final String LINE_SEPARATOR = "\n";
  private static final String ourEncoding = "utf-8";
  private final Map<FileType, Object> fileTypeToVersionOwner = new THashMap<FileType, Object>();
  private final TObjectLongHashMap<FileType> fileTypeToVersion = new TObjectLongHashMap<>();
  private final TLongObjectHashMap<FileType> versionToFileType = new TLongObjectHashMap<>();
  private long myStubIndexStamp;

  StubVersionMap() throws IOException {
    for (final FileType fileType : FileTypeRegistry.getInstance().getRegisteredFileTypes()) {
      Object owner = getVersionOwner(fileType);
      if (owner != null) {
        fileTypeToVersionOwner.put(fileType, owner);
      }
    }

    updateState();
  }

  private void updateState() throws IOException {
    final long currentStubIndexStamp = IndexingStamp.getIndexCreationStamp(StubUpdatingIndex.INDEX_ID);
    File allIndexedFiles = allIndexedFilesRegistryFile();

    List<String> removedFileTypes = new ArrayList<>();
    List<FileType> updatedFileTypes = new ArrayList<>();
    List<FileType> addedFileTypes = new ArrayList<>();
    long lastUsedCounter = currentStubIndexStamp;

    if (allIndexedFiles.lastModified() == currentStubIndexStamp) {
      FileTypeRegistry fileTypeRegistry = FileTypeRegistry.getInstance();

      Set<FileType> loadedFileTypes = new THashSet<>();

      for(String fileTypeInfo: StringUtil.split(FileUtil.loadFile(allIndexedFiles, ourEncoding), LINE_SEPARATOR)) {
        List<String> strings = StringUtil.split(fileTypeInfo, RECORD_SEPARATOR);
        String fileTypeName = strings.get(0);
        long usedTimeStamp = Long.parseLong(strings.get(2));
        lastUsedCounter = Math.min(lastUsedCounter, usedTimeStamp);

        FileType fileType = fileTypeRegistry.findFileTypeByName(fileTypeName);
        if (fileType == null) removedFileTypes.add(fileTypeName);
        else {
          loadedFileTypes.add(fileType);
          Object owner = getVersionOwner(fileType);
          if (owner == null) removedFileTypes.add(fileTypeName);
          else {
            if (!Comparing.equal(strings.get(1), typeAndVersion(owner))) {
              updatedFileTypes.add(fileType);
            } else {
              registerStamp(fileType, usedTimeStamp);
            }
          }
        }
      }

      for(FileType fileType:fileTypeToVersionOwner.keySet()) {
        if (!loadedFileTypes.contains(fileType)) {
          addedFileTypes.add(fileType);
        }
      }

      if (!addedFileTypes.isEmpty() || !removedFileTypes.isEmpty()) {
        StubUpdatingIndex.LOG.info("requesting complete stub index rebuild due to changes: " +
                                   (addedFileTypes.isEmpty() ? "" : "added file types:" + StringUtil.join(addedFileTypes, FileType::getName, ",") + ";") +
                                   (removedFileTypes.isEmpty() ? "":"removed file types:" + StringUtil.join(removedFileTypes, ",")));
        throw new IOException(); // StubVersionMap will be recreated
      }
    } else {
      addedFileTypes.addAll(fileTypeToVersionOwner.keySet());
    }

    long counter = lastUsedCounter - 1; // important to start with value smaller and progress downwards
    for(FileType fileType: ContainerUtil.concat(updatedFileTypes, addedFileTypes)) {
      while (versionToFileType.containsKey(counter)) --counter;
      registerStamp(fileType, counter);
    }

    if (!addedFileTypes.isEmpty() || !updatedFileTypes.isEmpty() || !removedFileTypes.isEmpty()) {
      if (!addedFileTypes.isEmpty()) {
        StubUpdatingIndex.LOG.info("Following new file types will be indexed:" + StringUtil.join(addedFileTypes, FileType::getName, ","));
      }

      if (!updatedFileTypes.isEmpty()) {
        StubUpdatingIndex.LOG.info("Stub version was changed for " + StringUtil.join(updatedFileTypes, FileType::getName, ","));
      }

      if (!removedFileTypes.isEmpty()) {
        StubUpdatingIndex.LOG.info("Following file types will not be indexed:" + StringUtil.join(removedFileTypes, ","));
      }

      StringBuilder allFileTypes = new StringBuilder();

      for (FileType fileType : fileTypeToVersionOwner.keySet()) {
        Object owner = fileTypeToVersionOwner.get(fileType);
        long timestamp = fileTypeToVersion.get(fileType);
        allFileTypes.append(fileType.getName()).append(RECORD_SEPARATOR).append(typeAndVersion(owner)).append(RECORD_SEPARATOR)
          .append(timestamp).append(LINE_SEPARATOR);
      }
      FileUtil.writeToFile(allIndexedFiles, allFileTypes.toString().getBytes(ourEncoding));
      FileUtil.setLastModified(allIndexedFiles, currentStubIndexStamp);
    }

    myStubIndexStamp = currentStubIndexStamp;
  }

  private void registerStamp(FileType fileTypeByName, long stamp) {
    fileTypeToVersion.put(fileTypeByName, stamp);
    FileType previousType = versionToFileType.put(stamp, fileTypeByName);
    if (previousType != null) {
      assert false;
    }
  }

  private static Object getVersionOwner(FileType fileType) {
    Object owner = null;
    if (fileType instanceof LanguageFileType) {
      Language l = ((LanguageFileType)fileType).getLanguage();
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(l);
      if (parserDefinition != null) {
        final IFileElementType type = parserDefinition.getFileNodeType();
        if (type instanceof IStubFileElementType) {
          owner = type;
        }
      }
    }

    BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
    if (builder != null) {
      owner = builder;
    }
    return owner;
  }

  public long getStamp(FileType type) {
    return fileTypeToVersion.get(type);
  }

  void clear() {
    fileTypeToVersion.clear();
    versionToFileType.clear();
    try {
      updateState();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @NotNull
  private static File allIndexedFilesRegistryFile() {
    return new File(new File(IndexInfrastructure.getIndexRootDir(StubUpdatingIndex.INDEX_ID), ".fileTypes"), INDEXED_FILETYPES);
  }

  @NotNull
  private static String typeAndVersion(Object owner) {
    return info(owner) + "," + version(owner);
  }

  private static String info(Object owner) {
    if (owner instanceof IStubFileElementType) {
      return "stub:" + owner.getClass().getName();
    } else {
      return "binary stub builder:" + owner.getClass().getName();
    }
  }

  private static int version(Object owner) {
    if (owner instanceof IStubFileElementType) {
      return ((IStubFileElementType)owner).getStubVersion();
    } else {
      return ((BinaryFileStubBuilder)owner).getStubVersion();
    }
  }

  public int getIndexingTimestampDiffForFileType(FileType type) {
    return (int)(myStubIndexStamp - fileTypeToVersion.get(type));
  }

  public @Nullable FileType getFileTypeByIndexingTimestampDiff(int diff) {
    return versionToFileType.get(myStubIndexStamp - diff);
  }
}