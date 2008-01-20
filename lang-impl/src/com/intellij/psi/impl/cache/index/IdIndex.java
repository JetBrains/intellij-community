package com.intellij.psi.impl.cache.index;

import com.intellij.ide.highlighter.custom.impl.CustomFileType;
import com.intellij.lang.cacheBuilder.CacheBuilderRegistry;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.cache.impl.idCache.IdTableBuilding;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IndexDataConsumer;
import com.intellij.util.indexing.ScalarIndexExtension;
import com.intellij.util.io.PersistentEnumerator;
import org.jetbrains.annotations.NonNls;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 16, 2008
 */
public class IdIndex extends ScalarIndexExtension<IdIndexEntry>{
  @NonNls public static final String NAME = "IdIndex";
  
  private FileBasedIndex.InputFilter myInputFilter = new FileBasedIndex.InputFilter() {
    private FileTypeManager myFtManager = FileTypeManager.getInstance();
    public boolean acceptInput(final VirtualFile file) {
      return isIndexable(myFtManager.getFileTypeByFile(file));
    }
  };

  private PersistentEnumerator.DataDescriptor<IdIndexEntry> myKeyDescriptor = new PersistentEnumerator.DataDescriptor<IdIndexEntry>() {
    public int getHashCode(final IdIndexEntry value) {
      return value.hashCode();
    }

    public boolean isEqual(final IdIndexEntry val1, final IdIndexEntry val2) {
      return val1.equals(val2);
    }

    public void save(final DataOutput out, final IdIndexEntry value) throws IOException {
      out.writeInt(value.getWordHashCode());
      out.writeInt(value.getOccurrenceMask());
    }

    public IdIndexEntry read(final DataInput in) throws IOException {
      return new IdIndexEntry(in.readInt(), in.readInt());
    }
  };
  
  private DataIndexer<IdIndexEntry, Void, FileBasedIndex.FileContent> myIndexer = new DataIndexer<IdIndexEntry, Void, FileBasedIndex.FileContent>() {
    public void map(final FileBasedIndex.FileContent inputData, final IndexDataConsumer<IdIndexEntry, Void> consumer) {
      final VirtualFile file = inputData.file;
      IdTableBuilding.getFileTypeIndexer(file.getFileType(), file).map(inputData, consumer);
    }
  };
  
  public int getVersion() {
    return 5;
  }

  public String getName() {
    return NAME;
  }

  public DataIndexer<IdIndexEntry, Void, FileBasedIndex.FileContent> getIndexer() {
    return myIndexer;
  }

  public PersistentEnumerator.DataDescriptor<IdIndexEntry> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }
  
  private static boolean isIndexable(FileType fileType) {
    return IdTableBuilding.isIdIndexerRegistered(fileType) || 
           CacheBuilderRegistry.getInstance().getCacheBuilder(fileType) != null ||  
           fileType instanceof LanguageFileType || 
           fileType instanceof CustomFileType;
  }
  
}
