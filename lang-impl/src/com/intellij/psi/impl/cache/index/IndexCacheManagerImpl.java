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
import com.intellij.psi.impl.cache.impl.idCache.IdCacheUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.ValueContainer;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
  }

  public void dispose() {
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
    final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    final Set<PsiFile> allFiles = new HashSet<PsiFile>();
    for (IndexPattern indexPattern : IdCacheUtil.getIndexPatterns()) {
      final Collection<VirtualFile> files = fileBasedIndex.getContainingFiles(
        TodoIndex.NAME, 
        new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), 
        myProject
      );
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          for (VirtualFile file : files) {
            final PsiFile psiFile = myPsiManager.findFile(file);
            if (psiFile != null) {
              allFiles.add(psiFile);
            }
          }
        }
        });
    }
    return allFiles.toArray(new PsiFile[allFiles.size()]);
  }

  public int getTodoCount(@NotNull final VirtualFile file, final IndexPatternProvider patternProvider) {
    final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    final int fileId = FileBasedIndex.getFileId(file);
    int count = 0;
    for (IndexPattern indexPattern : patternProvider.getIndexPatterns()) {
      count += fetchCount(fileBasedIndex, fileId, indexPattern);
    }
    return count;
  }
   
  public int getTodoCount(@NotNull final VirtualFile file, final IndexPattern pattern) {
    return fetchCount(FileBasedIndex.getInstance(), FileBasedIndex.getFileId(file), pattern);
  }

  private int fetchCount(final FileBasedIndex fileBasedIndex, final int fileId, final IndexPattern indexPattern) {
    final int[] count = new int[] {0};
    fileBasedIndex.getData(TodoIndex.NAME, new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), myProject, new FileBasedIndex.DataFilter<TodoIndexEntry, Integer>() {
      public List<Integer> process(final TodoIndexEntry entry, final ValueContainer<Integer> container) {
        for (final Iterator<Integer> valueIterator = container.getValueIterator(); valueIterator.hasNext(); ) {
          final Integer value = valueIterator.next();
          if (container.isAssociated(value, fileId)) {
            count[0] = value.intValue();
            return Collections.emptyList();
          }
        }
        return Collections.emptyList();
      }
    });
    return count[0];
  }

  public void addOrInvalidateFile(@NotNull final VirtualFile file) {
    // empty
  }
}
