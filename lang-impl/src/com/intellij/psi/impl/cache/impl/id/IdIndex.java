package com.intellij.psi.impl.cache.impl.id;

import com.intellij.ide.highlighter.custom.impl.CustomFileType;
import com.intellij.lang.cacheBuilder.CacheBuilderRegistry;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.psi.impl.search.CachesBasedRefSearcher;
import org.jetbrains.annotations.NonNls;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 16, 2008
 */
public class IdIndex implements FileBasedIndexExtension<IdIndexEntry, Integer> {
  @NonNls public static final ID<IdIndexEntry, Integer> NAME = new ID<IdIndexEntry, Integer>("IdIndex");
  
  private final FileBasedIndex.InputFilter myInputFilter = new FileBasedIndex.InputFilter() {
    private final FileTypeManager myFtManager = FileTypeManager.getInstance();
    public boolean acceptInput(final VirtualFile file) {
      return isIndexable(myFtManager.getFileTypeByFile(file));
    }
  };

  private final DataExternalizer<Integer> myValueExternalizer = new DataExternalizer<Integer>() {
    public void save(final DataOutput out, final Integer value) throws IOException {
      out.writeByte(value.intValue());
    }

    public Integer read(final DataInput in) throws IOException {
      return Integer.valueOf(in.readByte());
    }
  };
  
  private final PersistentEnumerator.DataDescriptor<IdIndexEntry> myKeyDescriptor = new PersistentEnumerator.DataDescriptor<IdIndexEntry>() {
    public int getHashCode(final IdIndexEntry value) {
      return value.hashCode();
    }

    public boolean isEqual(final IdIndexEntry val1, final IdIndexEntry val2) {
      return val1.equals(val2);
    }

    public void save(final DataOutput out, final IdIndexEntry value) throws IOException {
      out.writeInt(value.getWordHashCode());
    }

    public IdIndexEntry read(final DataInput in) throws IOException {
      return new IdIndexEntry(in.readInt());
    }
  };
  
  private final DataIndexer<IdIndexEntry, Integer, FileBasedIndex.FileContent> myIndexer = new DataIndexer<IdIndexEntry, Integer, FileBasedIndex.FileContent>() {
    public Map<IdIndexEntry, Integer> map(final FileBasedIndex.FileContent inputData) {
      final VirtualFile file = inputData.file;
      final FileTypeIdIndexer indexer = IdTableBuilding.getFileTypeIndexer(file.getFileType());
      if (CachesBasedRefSearcher.DEBUG) {
        System.out.println("fileName = " + inputData.fileName);
      }
      if (indexer != null) {
        return indexer.map(inputData);
      }

      return Collections.emptyMap();
    }
  };
  
  public int getVersion() {
    return 8;
  }

  public boolean dependsOnFileContent() {
    return true;
  }

  public ID<IdIndexEntry,Integer> getName() {
    return NAME;
  }

  public DataIndexer<IdIndexEntry, Integer, FileBasedIndex.FileContent> getIndexer() {
    return myIndexer;
  }

  public DataExternalizer<Integer> getValueExternalizer() {
    return myValueExternalizer;
  }

  public PersistentEnumerator.DataDescriptor<IdIndexEntry> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }
  
  private static boolean isIndexable(FileType fileType) {
    return fileType instanceof LanguageFileType ||
           fileType instanceof CustomFileType ||
           IdTableBuilding.isIdIndexerRegistered(fileType) ||
           CacheBuilderRegistry.getInstance().getCacheBuilder(fileType) != null;
  }

}
