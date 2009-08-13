package com.intellij.util.indexing;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 26, 2007
 * V class MUST have equals / hashcode properly defined!!!
 */
public abstract class FileBasedIndexExtension<K, V> {
  public static final ExtensionPointName<FileBasedIndexExtension> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.fileBasedIndex");
  public static final int DEFAULT_CACHE_SIZE = 1024;

  public abstract ID<K, V> getName();
  
  public abstract DataIndexer<K, V, FileContent> getIndexer();
  
  public abstract KeyDescriptor<K> getKeyDescriptor();
  
  public abstract DataExternalizer<V> getValueExternalizer();
  
  public abstract FileBasedIndex.InputFilter getInputFilter();
  
  public abstract boolean dependsOnFileContent();
  
  public abstract int getVersion();

  /**
   * @see FileBasedIndexExtension#DEFAULT_CACHE_SIZE
   */
  public int getCacheSize() {
    return DEFAULT_CACHE_SIZE;
  }

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
  public Collection<FileType> getFileTypesWithSizeLimitNotApplicable() {
    return Collections.emptyList();
  }
}
