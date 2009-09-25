/*
 * @author max
 */
package com.intellij.find.ngrams;

import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class TrigramIndex extends ScalarIndexExtension<Integer> {
  public static final boolean ENABLED = "true".equals(System.getProperty("idea.internal.trigramindex.enabled"));

  public static final ID<Integer,Void> INDEX_ID = ID.create("Trigram.Index");

  private static final FileBasedIndex.InputFilter INPUT_FILTER = new FileBasedIndex.InputFilter() {
    public boolean acceptInput(VirtualFile file) {
      return !file.getFileType().isBinary();
    }
  };
  private static final FileBasedIndex.InputFilter NO_FILES = new FileBasedIndex.InputFilter() {
    public boolean acceptInput(VirtualFile file) {
      return false;
    }
  };

  public ID<Integer, Void> getName() {
    return INDEX_ID;
  }

  public DataIndexer<Integer, Void, FileContent> getIndexer() {
    return new DataIndexer<Integer, Void, FileContent>() {
      @NotNull
      public Map<Integer, Void> map(FileContent inputData) {
        final Map<Integer, Void> result = new THashMap<Integer, Void>();
        TIntHashSet built = TrigramBuilder.buildTrigram(inputData.getContentAsText());
        built.forEach(new TIntProcedure() {
          public boolean execute(int value) {
            result.put(value, null);
            return true;
          }
        });
        return result;
      }
    };
  }

  public KeyDescriptor<Integer> getKeyDescriptor() {
    return new EnumeratorIntegerDescriptor();
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    if (ENABLED) {
      return INPUT_FILTER;
    }
    else {
      return NO_FILES;
    }
  }

  public boolean dependsOnFileContent() {
    return true;
  }

  public int getVersion() {
    return ENABLED ? 2 : 1;
  }
}
