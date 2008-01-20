package com.intellij.psi.impl.cache.index;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 16, 2008
 */
public class IndexCacheManagerImpl implements CacheManager{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.cache.index.IndexCacheManagerImpl");
  private final Project myProject;
  private final PsiManager myPsiManager;

  public IndexCacheManagerImpl(PsiManager psiManager) {
    myPsiManager = psiManager;
    myProject = psiManager.getProject();
  }

  public void initialize() {
    FileBasedIndex.getInstance().projectJoins(myProject);
  }

  public void dispose() {
    FileBasedIndex.getInstance().projectLeaves(myProject);
  }

  @NotNull
  public CacheUpdater[] getCacheUpdaters() {
    return new CacheUpdater[0]; // do not expose own updaters
  }

  @NotNull
  public synchronized PsiFile[] getFilesWithWord(@NotNull final String word, final short occurenceMask, @NotNull final GlobalSearchScope scope, final boolean caseSensitively) {
    CommonProcessors.CollectProcessor<PsiFile> processor = new CommonProcessors.CollectProcessor<PsiFile>();
    processFilesWithWord(processor, word, occurenceMask, scope, caseSensitively);
    return processor.toArray(PsiFile.EMPTY_ARRAY);
  }

  public synchronized boolean processFilesWithWord(@NotNull final Processor<PsiFile> psiFileProcessor, @NotNull final String word, final short occurrenceMask, @NotNull final GlobalSearchScope scope, final boolean caseSensitively) {

    Processor<VirtualFile> virtualFileProcessor = new Processor<VirtualFile>() {
      public boolean process(final VirtualFile virtualFile) {
        LOG.assertTrue(virtualFile.isValid());
        return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          public Boolean compute() {
            if (scope.contains(virtualFile)) {
              final PsiFile psiFile = myPsiManager.findFile(virtualFile);
              return psiFile == null || psiFileProcessor.process(psiFile);
            }
            return Boolean.TRUE;
          }
        });
      }
    };

    final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    final Set<VirtualFile> vFiles = new HashSet<VirtualFile>();
    final int stop = (((int)UsageSearchContext.ANY) & 0xFF) + 1;
    for (int mask = 0x1; mask < stop; mask <<= 1) {
      if ((mask & occurrenceMask) != 0) {
        vFiles.addAll(
          fileBasedIndex.getContainingFiles(IdIndex.NAME, new IdIndexEntry(word, mask, caseSensitively), myProject)
        );
      }
    }
    
    for (VirtualFile vFile : vFiles) {
      if (!virtualFileProcessor.process(vFile)) {
        return false;
      }
    }

    return true;
  }

  @NotNull
  public PsiFile[] getFilesWithTodoItems() {
    return PsiFile.EMPTY_ARRAY; // todo
  }

  public int getTodoCount(@NotNull final VirtualFile file, final IndexPatternProvider patternProvider) {
    return 0; // todo
  }

  public int getTodoCount(@NotNull final VirtualFile file, final IndexPattern pattern) {
    return 0; // todo
  }

  public void addOrInvalidateFile(@NotNull final VirtualFile file) {
    // empty
  }
}
