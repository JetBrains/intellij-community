package com.intellij.util.indexing;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 26, 2007
 * V class MUST have equals / hashcode properly defined!!!
 */
public interface FileBasedIndexExtension<K, V> {
  ExtensionPointName<FileBasedIndexExtension> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.fileBasedIndex");
  int DEFAULT_CACHE_SIZE = 1024;

  ID<K, V> getName();
  
  DataIndexer<K, V, FileContent> getIndexer();
  
  KeyDescriptor<K> getKeyDescriptor();
  
  DataExternalizer<V> getValueExternalizer();
  
  FileBasedIndex.InputFilter getInputFilter();
  
  boolean dependsOnFileContent();
  
  int getVersion();

  /**
   * @see FileBasedIndexExtension#DEFAULT_CACHE_SIZE
   */
  int getCacheSize();

  /**
   * For most indices the method should return an empty collection.
   * @return collection of file types to which file size limit will not be applied when indexing.
   * This is the way to allow indexing of files whose limit exceeds FileManagerImpl.MAX_INTELLISENSE_FILESIZE.
   *
   * Use carefully, because indexing large files may influence index update speed dramatically.
   *
   * @see com.intellij.psi.impl.file.impl.FileManagerImpl#MAX_INTELLISENSE_FILESIZE
   */
  @NotNull
  Collection<FileType> getFileTypesWithSizeLimitNotApplicable();
}
