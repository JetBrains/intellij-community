package com.intellij.psi.impl.cache.impl.todo;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.PersistentEnumerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 20, 2008
 */
public class TodoIndex implements FileBasedIndexExtension<TodoIndexEntry, Integer> {

  @NonNls public static final ID<TodoIndexEntry, Integer> NAME = ID.create("TodoIndex");

  public TodoIndex(TodoConfiguration config) {
    config.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(final PropertyChangeEvent evt) {
        if (IndexPatternProvider.PROP_INDEX_PATTERNS.equals(evt.getPropertyName())) {
          FileBasedIndex.getInstance().requestRebuild(NAME);
        }
      }
    });
  }

  private final PersistentEnumerator.DataDescriptor<TodoIndexEntry> myKeyDescriptor = new PersistentEnumerator.DataDescriptor<TodoIndexEntry>() {
    public int getHashCode(final TodoIndexEntry value) {
      return value.hashCode();
    }

    public boolean isEqual(final TodoIndexEntry val1, final TodoIndexEntry val2) {
      return val1.equals(val2);
    }

    public void save(final DataOutput out, final TodoIndexEntry value) throws IOException {
      out.writeUTF(value.pattern);
      out.writeBoolean(value.caseSensitive);
    }

    public TodoIndexEntry read(final DataInput in) throws IOException {
      final String pattern = in.readUTF();
      final boolean caseSensitive = in.readBoolean();
      return new TodoIndexEntry(pattern, caseSensitive);
    }
  };
  
  private final DataExternalizer<Integer> myValueExternalizer = new DataExternalizer<Integer>() {
    public void save(final DataOutput out, final Integer value) throws IOException {
      out.writeInt(value.intValue());
    }

    public Integer read(final DataInput in) throws IOException {
      return Integer.valueOf(in.readInt());
    }
  };

  private final DataIndexer<TodoIndexEntry, Integer, FileContent> myIndexer = new DataIndexer<TodoIndexEntry, Integer, FileContent>() {
    @NotNull
    public Map<TodoIndexEntry,Integer> map(final FileContent inputData) {
      final VirtualFile file = inputData.getFile();
      final DataIndexer<TodoIndexEntry, Integer, FileContent> indexer = IdTableBuilding.getTodoIndexer(file.getFileType(), file);
      if (indexer != null) {
        return indexer.map(inputData);
      }
      return Collections.emptyMap();
    }
  };
  
  private final FileBasedIndex.InputFilter myInputFilter = new FileBasedIndex.InputFilter() {
    private final FileTypeManager myFtManager = FileTypeManager.getInstance();
    public boolean acceptInput(final VirtualFile file) {
      if (!(file.getFileSystem() instanceof LocalFileSystem)) {
        return false; // do not index TODOs in library sources
      }

      final FileType fileType = myFtManager.getFileTypeByFile(file);
      if (ProjectUtil.isProjectOrWorkspaceFile(file, fileType)) {
        return false;
      }
      
      if (fileType instanceof LanguageFileType) {
        final Language lang = ((LanguageFileType)fileType).getLanguage();
        final ParserDefinition parserDef = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
        final TokenSet commentTokens = parserDef != null ? parserDef.getCommentTokens() : null;
        return commentTokens != null;
      }
      
      return IdTableBuilding.isTodoIndexerRegistered(fileType) ||
             fileType instanceof AbstractFileType;
    }
  };

  public int getCacheSize() {
    return DEFAULT_CACHE_SIZE;
  }

  public int getVersion() {
    return 3;
  }

  public boolean dependsOnFileContent() {
    return true;
  }

  public ID<TodoIndexEntry, Integer> getName() {
    return NAME;
  }

  public DataIndexer<TodoIndexEntry, Integer, FileContent> getIndexer() {
    return myIndexer;
  }

  public PersistentEnumerator.DataDescriptor<TodoIndexEntry> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  public DataExternalizer<Integer> getValueExternalizer() {
    return myValueExternalizer;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

}
