package com.intellij.psi.search;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentEnumerator;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author yole
 */
public class FilenameIndex extends ScalarIndexExtension<String> {
  @NonNls public static final ID<String, Void> NAME = ID.create("FilenameIndex");
  private final MyDataIndexer myDataIndexer = new MyDataIndexer();
  private final MyInputFilter myInputFilter = new MyInputFilter();
  private final EnumeratorStringDescriptor myKeyDescriptor = new EnumeratorStringDescriptor();

  public ID<String,Void> getName() {
    return NAME;
  }

  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  public PersistentEnumerator.DataDescriptor<String> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  public boolean dependsOnFileContent() {
    return false;
  }

  public int getVersion() {
    return 0;
  }

  public static String[] getAllFilenames() {
    final Collection<String> allKeys = FileBasedIndex.getInstance().getAllKeys(NAME);
    return allKeys.toArray(new String[allKeys.size()]);
  }

  public static PsiFile[] getFilesByName(final Project project, final String name, final GlobalSearchScope scope) {
    final Collection<VirtualFile> files = FileBasedIndex.getInstance().getContainingFiles(NAME, name, VirtualFileFilter.ALL);
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

  private static class MyDataIndexer implements DataIndexer<String, Void, FileContent> {
    public Map<String, Void> map(final FileContent inputData) {
      return Collections.singletonMap(inputData.getFileName(), null);
    }
  }

  private static class MyInputFilter implements FileBasedIndex.InputFilter {
    public boolean acceptInput(final VirtualFile file) {
      return !ProjectUtil.isProjectOrWorkspaceFile(file);
    }
  }
}
