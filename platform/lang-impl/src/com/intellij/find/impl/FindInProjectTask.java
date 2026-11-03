// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.find.FindBundle;
import com.intellij.find.FindInProjectSearchEngine.FindInProjectSearcher;
import com.intellij.find.FindModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.FindUsagesProcessPresentation;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.function.Predicate;

/**
 * One "Find in Path" search: a candidate pipeline with one producer and one work queue of worker coroutines.
 * <ul>
 *   <li>{@link FindSearchRun} produces the candidates in three phases on the thread of {@link #findUsages}, into a
 *   {@link FindWorkQueue}, whose workers process them; the queue's KDoc describes the life of a work item.</li>
 *   <li>{@link SearcherSet} wraps the {@link FindInProjectSearcher}s: candidate files, coverage
 *   ({@link com.intellij.find.FindInProjectSearchEngine.Coverage}), covered files, scan hooks.</li>
 *   <li>{@link CandidateFilter} admits each candidate in two stages: cheap checks on the producer, the rest on a worker.</li>
 *   <li>{@link ScopeWalker} plans the walk of the scope and expands its items on the workers.</li>
 *   <li>{@link FileScanner} checks an admitted file for the pattern and delivers its usages.</li>
 * </ul>
 * The constructor derives the model facts (directory, module, mask, exclusion rule) and builds the parts.
 */
final class FindInProjectTask {
  private static final Logger LOG = Logger.getInstance(FindInProjectTask.class);

  private final FindModel findModel;

  /**
   * Files to check for the pattern at first, before other candidates are appended from searchers.
   * Those files are only candidates -- i.e. they will still be checked for a pattern, just checked before any other candidates.
   * Usually previously found files are supplied here, to provide better UX in 'incremental search', so that already found files
   * are not re-ordered on each next key typed.
   */
  private final Set<? extends VirtualFile> filesToScanInitially;

  private final @NotNull SearcherSet searchers;

  private final Project project;
  private final ProjectFileIndex projectFileIndex;
  /** The PSI manager of {@link #project}, see {@link #psiManagerFor}. */
  private final PsiManager psiManager;

  /** See {@link #isExcludedFromSearch(VirtualFile)}. */
  private final boolean skipExcludedFiles;
  /**
   * The paths of the searched directories that are excluded themselves; the user asked for them, so their files are searched.
   * A path, because the searched directory can be a transient VFS file while a directory search engine gives cached files.
   */
  private final List<String> excludedRootPaths;

  private final @NotNull CandidateFilter candidateFilter;
  private final @NotNull ScopeWalker scopeWalker;

  private final ProgressIndicator progressIndicator;

  FindInProjectTask(@NotNull FindModel findModel,
                    @NotNull Project project,
                    @NotNull Set<? extends VirtualFile> filesToScanInitially,
                    boolean tooManyUsagesStatus) {
    this.findModel = findModel;
    this.project = project;
    this.filesToScanInitially = filesToScanInitially;

    searchers = SearcherSet.create(findModel, project);

    psiManager = PsiManager.getInstance(project);
    projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    String moduleName = findModel.getModuleName();
    Module moduleToSearchIn = moduleName == null ?
                              null :
                              ReadAction.computeBlocking(() -> ModuleManager.getInstance(project).findModuleByName(moduleName));

    List<VirtualFile> directoriesToSearchIn;
    boolean withSubdirectories;
    boolean directoryStandsForProject = false;
    var directoryCandidate = FindInProjectUtil.getDirectory(findModel);
    // a module search walks the module, and the module scope filters only the index candidates, so the directories stay out of it
    List<VirtualFile> projectDirectories = directoryCandidate == null && moduleToSearchIn == null ? projectDirectories(project) : null;
    if (projectDirectories != null) {
      // A directory search is a set of files that holds every file of the requested scope, and maybe more: the scope filters
      // each discovered file later. A DirectorySearchEngine can delegate the walk and the pre-filtering of a directory to a
      // backend, so a product whose project is a set of directories searches the project this way.
      directoriesToSearchIn = projectDirectories;
      if (directoriesToSearchIn.isEmpty()) {
        LOG.warn("No project directory found for search in " + project);
      }
      else {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Using the project directories " + directoriesToSearchIn + " for search.");
        }
        directoryStandsForProject = true;
      }
      withSubdirectories = true;
    }
    else {
      directoriesToSearchIn = ContainerUtil.createMaybeSingletonList(directoryCandidate);
      withSubdirectories = findModel.isWithSubdirectories();
    }

    skipExcludedFiles = !directoriesToSearchIn.isEmpty() && !Registry.is("find.search.in.excluded.dirs");
    excludedRootPaths = !skipExcludedFiles ? List.of() : ReadAction.computeBlocking(
      () -> ContainerUtil.mapNotNull(directoriesToSearchIn, dir -> projectFileIndex.isExcluded(dir) ? dir.getPath() : null)
    );

    SearchScope modelCustomScope = findModel.isCustomScope() ? findModel.getCustomScope() : null;
    GlobalSearchScope customScope = modelCustomScope == null ? null : GlobalSearchScopeUtil.toGlobalSearchScope(modelCustomScope, project);

    boolean locateClassSources = !directoriesToSearchIn.isEmpty()
                                 && ReadAction.computeBlocking(() -> ContainerUtil.exists(directoriesToSearchIn, dir -> projectFileIndex.getClassRootForFile(dir) != null));

    Predicate<CharSequence> fileNamePatternCondition = FindInProjectUtil.createFileMaskCondition(findModel.getFileFilter());
    NotNullLazyValue<GlobalSearchScope> modelScope =
      NotNullLazyValue.atomicLazy(() -> FindInProjectUtil.getGlobalSearchScope(project, findModel));
    candidateFilter = new CandidateFilter(
      file -> fileNamePatternCondition.test(file.getNameSequence()),
      this::isExcludedFromSearch,
      file -> modelScope.getValue().contains(file),
      file -> customScope == null || customScope.contains(file),
      searchers::isCoveredByReliable,
      locateClassSources,
      file -> {
        var pair = FileScanner.findFile(psiManagerFor(file), file);
        return pair == null ? null : pair.second;
      }
    );
    scopeWalker = new ScopeWalker(findModel, project, candidateFilter, moduleToSearchIn, directoriesToSearchIn, withSubdirectories,
                                  directoryStandsForProject, customScope);

    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    progressIndicator = progress != null ?
                        progress :
                        new EmptyProgressIndicator();

    if (tooManyUsagesStatus) {
      TooManyUsagesStatus.createFor(progressIndicator);
    }
  }

  /**
   * The PSI manager that builds the PSI of {@code file}. A usage belongs to the project of its PSI file, and it opens there.
   * A file in the content of {@link #project} keeps the project, also when another open project holds it too.
   * A file that {@link #project} excludes goes to the open project whose content holds it, else it keeps the project:
   * the user asked for the excluded directory. Any other file goes to the first open project that holds it, as content or
   * excluded. A file no open project holds stays with the project of the search, as a library file or a file of a
   * {@link com.intellij.find.FindModelExtension} does. Under a read action.
   */
  private @NotNull PsiManager psiManagerFor(@NotNull VirtualFile file) {
    if (projectFileIndex.isInContent(file)) return psiManager;
    Collection<Project> holders = ProjectLocator.getInstance().getProjectsForFile(file);
    Project owner = ContainerUtil.find(holders, p -> p != project && !p.isDisposed() && ProjectFileIndex.getInstance(p).isInContent(file));
    if (owner == null && !projectFileIndex.isExcluded(file)) {
      owner = ContainerUtil.find(holders, p -> p != project && !p.isDisposed());
    }
    return owner == null ? psiManager : PsiManager.getInstance(owner);
  }

  /**
   * The valid directories that stand for the project, from the {@link FindInProjectDirectoriesProvider} that defines it.
   * Null when no provider does, so the project search takes the platform path.
   */
  private static @Nullable List<VirtualFile> projectDirectories(@NotNull Project project) {
    if (project.isDefault()) return null;
    List<VirtualFile> directories = FindInProjectDirectoriesProvider.findProjectDirectories(project);
    return directories == null ? null : ContainerUtil.filter(directories, VirtualFile::isValid);
  }

  /**
   * Find all usages of a given pattern in a given set of files, and deliver them to the usageProcessor. The pattern, set of files,
   * and most other details are defined by {@link #findModel}.
   * <p>
   * To better understand find usage code take into account that find usage is not an abstract task -- it is heavily tailored to
   * the specific needs and user's expectations of FindUsage UX.
   * <p>
   * This thread produces candidates in three phases into a {@link FindWorkQueue} (see {@link FindSearchRun}); its worker
   * coroutines check them in read actions (see {@link CandidateFilter}) and scan them (see {@link FileScanner}).
   * Each phase drains before the next one starts.
   * <ol>
   *   <li>
   *     'Priority': the open files and {@link #filesToScanInitially} -- the files found previously. We re-check them so that
   *     the files, which match before and still match now, are remaining at the top, and so that they fill the result cap first.
   *     This provides better UX when the search pattern is expanded as the user types additional symbols.
   *   </li>
   *   <li>
   *     'Fast search': query the {@link #searchers} for candidate files. Searchers represent a 'fast' way of finding
   *     the matching candidates -- i.e., some kind of index. The files returned by the searchers are only candidates -- they
   *     still must be checked against the file mask and the pattern. The collecting searchers answer first, and their files
   *     are queued sorted; the files of a streaming searcher (see {@link FindInProjectSearcher#isStreaming()}) are queued
   *     while it still searches.
   *   </li>
   *   <li>
   *     'Brute force search': walk all files in the scope defined by {@link #findModel} (see {@link ScopeWalker}), plus the files
   *     of the {@link com.intellij.find.FindModelExtension}s, and process them, multithreaded, against fileMask, and the pattern.
   *     The files scanned in the earlier phases are skipped; so are the files covered by a reliable searcher
   *     (see {@link FindInProjectSearcher#isCovered}), and the non-indexable files when a reliable searcher returned
   *     {@link com.intellij.find.FindInProjectSearchEngine.Coverage#ALL_CANDIDATES}.
   *   </li>
   * </ol>
   * Those phases combined give us the chance to deliver indexed files results almost instantly, keep top results consistent
   * as the user continues typing in the search pattern, and still search extensively over (partially-)not-indexed scopes -- slower,
   * but still.
   */
  void findUsages(@NotNull FindUsagesProcessPresentation processPresentation,
                  @NotNull Processor<? super UsageInfo> usageProcessor) {
    if (!EDT.isCurrentThreadEdt()) {
      ThreadingAssertions.assertNoOwnReadAccess();
    }
    CoreProgressManager.assertUnderProgress(progressIndicator);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Searching for '" + findModel.getStringToFind() + "'");
    }
    long searchStartedAtNs = System.nanoTime();
    FileScanner fileScanner = null;
    try {
      fileScanner = new FileScanner(findModel, project, searchers, progressIndicator, processPresentation, usageProcessor,
                                    searchStartedAtNs, this::psiManagerFor);
      FindSearchRun run = new FindSearchRun(project, filesToScanInitially, searchers, candidateFilter, scopeWalker, fileScanner,
                                            progressIndicator, searchStartedAtNs);
      try {
        if (EDT.isCurrentThreadEdt()) {
          runOffEdt(run);
        }
        else {
          run.runBlocking();
        }
      }
      catch (FindAbort e) {
        // the internal failure keeps its type: a plain CancellationException cancels, anything else is logged below
        ExceptionUtil.rethrow(e.unwrap());
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Search completed in " + TimeoutUtil.getDurationMillis(searchStartedAtNs) + " ms");
      }
    }
    catch (CancellationException e) {
      processPresentation.setCanceled(true);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Search canceled after " + TimeoutUtil.getDurationMillis(searchStartedAtNs) + " ms", new Exception(e));
      }
      throw e;
    }
    catch (Throwable th) {
      LOG.error(th);
    }

    if (fileScanner != null && !fileScanner.getLargeFiles().isEmpty()) {
      processPresentation.setLargeFilesWereNotScanned(fileScanner.getLargeFiles());
    }

    if (!progressIndicator.isCanceled()) {
      progressIndicator.setText(FindBundle.message("find.progress.search.completed"));
    }
  }

  /**
   * Runs the search on a pooled thread while the EDT waits.
   * Only tests call {@link #findUsages} on the EDT (e.g. {@code FindManagerTest}): inside {@code runBlockingCancellable} the EDT
   * loses its read access, and the producer needs a background thread for its non-blocking read actions (the walk roots).
   * An outside cancel ends the wait at once; the pooled search then ends on its own.
   */
  private void runOffEdt(@NotNull FindSearchRun run) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error("Find in Files runs on the EDT");
    }
    Future<Throwable> search = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        ProgressManager.getInstance().runProcess(() -> {
          run.runBlocking();
        }, ProgressWrapper.wrap(progressIndicator));
        return null;
      }
      catch (Throwable e) {
        return e; // rethrown on the EDT below, control flow exceptions included
      }
    });
    ExceptionUtil.rethrowAllAsUnchecked(ProgressIndicatorUtils.awaitWithCheckCanceled(search, progressIndicator));
  }

  /**
   * A directory search skips the excluded files, unless the registry key allows them or the file lies under a searched directory
   * that is excluded itself. One excluded directory does not open the excluded files of the other searched directories.
   * The check compares paths: the searched directory and the file can be different VFS objects for one disk directory.
   */
  private boolean isExcludedFromSearch(@NotNull VirtualFile file) {
    return skipExcludedFiles
           && projectFileIndex.isExcluded(file)
           && !ContainerUtil.exists(excludedRootPaths, rootPath -> VfsUtilCore.isAncestorOrSelf(rootPath, file));
  }
}
