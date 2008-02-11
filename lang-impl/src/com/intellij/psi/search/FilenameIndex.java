package com.intellij.psi.search;

import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.ScalarIndexExtension;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * @author yole
 */
public class FilenameIndex extends ScalarIndexExtension<String> {
  @NonNls public static final String NAME = "FilenameIndex";
  private final MyDataIndexer myDataIndexer = new MyDataIndexer();
  private final MyDataDescriptor myDataDescriptor = new MyDataDescriptor();
  private final MyInputFilter myInputFilter = new MyInputFilter();

  public String getName() {
    return NAME;
  }

  public DataIndexer<String, Void, FileBasedIndex.FileContent> getIndexer() {
    return myDataIndexer;
  }

  public PersistentEnumerator.DataDescriptor<String> getKeyDescriptor() {
    return myDataDescriptor;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  public int getVersion() {
    return 0;
  }

  public static String[] getAllFilenames() {
    final Collection<String> allKeys = FileBasedIndex.getInstance().getAllKeys(NAME);
    return allKeys.toArray(new String[allKeys.size()]);
  }

  public static PsiFile[] getFilesByName(final Project project, final String name, final GlobalSearchScope scope) {
    final Collection<VirtualFile> files = FileBasedIndex.getInstance().getContainingFiles(NAME, name, project);
    if (files.isEmpty()) return PsiFile.EMPTY_ARRAY;
    List<PsiFile> result = new ArrayList<PsiFile>();
    for(VirtualFile file: files) {
      if (!file.isValid() || !scope.contains(file)) continue;
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile != null) {
        result.add(psiFile);
      }
    }
    return result.toArray(new PsiFile[result.size()]);
  }

  private static class MyDataIndexer implements DataIndexer<String, Void, FileBasedIndex.FileContent> {
    public Map<String, Void> map(final FileBasedIndex.FileContent inputData) {
      return Collections.singletonMap(inputData.fileName, null);
    }
  }

  private static class MyDataDescriptor implements PersistentEnumerator.DataDescriptor<String> {
    private final byte[] buffer = IOUtil.allocReadWriteUTFBuffer();

    public int getHashCode(final String value) {
      return value.hashCode();
    }

    public boolean isEqual(final String val1, final String val2) {
      return val1.equals(val2);
    }

    public void save(final DataOutput out, final String value) throws IOException {
      IOUtil.writeUTFFast(buffer, out, value);
    }

    public String read(final DataInput in) throws IOException {
      return IOUtil.readUTFFast(buffer, in);
    }
  }

  private static class MyInputFilter implements FileBasedIndex.InputFilter {
    public boolean acceptInput(final VirtualFile file) {
      return true;
    }
  }
}
