// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.concurrency.JobLauncher;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.util.NavigationItemListCellRenderer;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.ChooseByNameContributorEx2;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.*;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FindSymbolParameters;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * Contributor-based goto model
 */
public abstract class ContributorsBasedGotoByModel implements ChooseByNameModelEx, PossiblyDumbAware {
  public static final Logger LOG = Logger.getInstance(ContributorsBasedGotoByModel.class);

  protected final Project myProject;
  private final List<ChooseByNameContributor> myContributors;

  protected ContributorsBasedGotoByModel(@NotNull Project project, ChooseByNameContributor @NotNull [] contributors) {
    this(project, List.of(contributors));
  }

  protected ContributorsBasedGotoByModel(@NotNull Project project, @NotNull List<ChooseByNameContributor> contributors) {
    myProject = project;
    myContributors = contributors;
  }

  @Override
  public boolean isDumbAware() {
    return ContainerUtil.find(getContributorList(), o -> DumbService.isDumbAware(o)) != null;
  }

  @Override
  public @NotNull ListCellRenderer<?> getListCellRenderer() {
    return new NavigationItemListCellRenderer();
  }

  public boolean sameNamesForProjectAndLibraries() {
    return false;
  }

  private final ConcurrentMap<ChooseByNameContributor, IntSet> myContributorToItsSymbolsMap = CollectionFactory.createConcurrentWeakMap();

  @Override
  public void processNames(@NotNull Processor<? super String> nameProcessor, @NotNull FindSymbolParameters parameters) {
    long start = System.currentTimeMillis();
    List<ChooseByNameContributor> contributors = filterDumb(getContributorList());
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    Processor<ChooseByNameContributor> processor = new ReadActionProcessor<>() {
      @Override
      public boolean processInReadAction(@NotNull ChooseByNameContributor contributor) {
        try {
          if (!myProject.isDisposed()) {
            long contributorStarted = System.currentTimeMillis();
            processContributorNames(contributor, parameters, nameProcessor);

            if (LOG.isDebugEnabled()) {
              LOG.debug(contributor + " for " + (System.currentTimeMillis() - contributorStarted));
            }
          }
        }
        catch (ProcessCanceledException | IndexNotReadyException ex) {
          // index corruption detected, ignore
        }
        catch (Throwable ex) {
          LOG.error(ex);
        }
        return true;
      }
    };
    if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(contributors, indicator, processor)) {
      throw new ProcessCanceledException();
    }
    if (indicator != null) {
      indicator.checkCanceled();
    }
    long finish = System.currentTimeMillis();
    if (LOG.isDebugEnabled()) {
      LOG.debug("processNames(): "+(finish-start)+"ms;");
    }
  }

  public void processContributorNames(@NotNull ChooseByNameContributor contributor,
                                      @NotNull FindSymbolParameters parameters,
                                      @NotNull Processor<? super String> nameProcessor) {
    IntSet filter = new IntOpenHashSet(1000);
    if (contributor instanceof ChooseByNameContributorEx2) {
      ((ChooseByNameContributorEx2)contributor).processNames(s -> {
        if (nameProcessor.process(s)) {
          filter.add(s.hashCode());
        }
        return true;
      }, parameters);
    }
    else if (contributor instanceof ChooseByNameContributorEx) {
      ((ChooseByNameContributorEx)contributor).processNames(s -> {
        if (nameProcessor.process(s)) {
          filter.add(s.hashCode());
        }
        return true;
      }, parameters.getSearchScope(), parameters.getIdFilter());
    }
    else {
      String[] names = contributor.getNames(myProject, parameters.isSearchInLibraries());
      for (String element : names) {
        if (nameProcessor.process(element)) {
          filter.add(element.hashCode());
        }
      }
    }
    myContributorToItsSymbolsMap.put(contributor, filter);
  }

  @Override
  public String @NotNull [] getNames(final boolean checkBoxState) {
    Set<String> allNames = new HashSet<>();

    Collection<String> result = Collections.synchronizedCollection(allNames);
    processNames(Processors.cancelableCollectProcessor(result),
                 FindSymbolParameters.simple(myProject, checkBoxState));
    if (LOG.isDebugEnabled()) {
      LOG.debug("getNames(): (got "+allNames.size()+" elements)");
    }
    return ArrayUtilRt.toStringArray(allNames);
  }

  private List<ChooseByNameContributor> filterDumb(List<? extends ChooseByNameContributor> contributors) {
    return ContainerUtil.filter(contributors, contributor -> DumbService.getInstance(myProject).isUsableInCurrentContext(contributor));
  }

  public Object @NotNull [] getElementsByName(@NotNull String name,
                                              @NotNull FindSymbolParameters parameters,
                                              @NotNull ProgressIndicator canceled) {
    Map<ChooseByNameContributor, String> applicable = new HashMap<>();
    for (ChooseByNameContributor contributor : filterDumb(getContributorList())) {
      IntSet filter = myContributorToItsSymbolsMap.get(contributor);
      if (filter == null || filter.contains(name.hashCode())) {
        applicable.put(contributor, name);
      }
    }
    if (applicable.isEmpty()) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    long start = System.nanoTime();
    List<NavigationItem> items = Collections.synchronizedList(new ArrayList<>());

    Processor<ChooseByNameContributor> processor = contributor ->
      processContributorForName(contributor, applicable.get(contributor), parameters, canceled, items);
    if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<>(applicable.keySet()), canceled, processor)) {
      canceled.cancel();
    }
    canceled.checkCanceled(); // if parallel job execution was canceled because of PCE, rethrow it from here
    if (LOG.isDebugEnabled()) {
      LOG.debug("Retrieving " + name + ":" + items.size() + " for " + TimeoutUtil.getDurationMillis(start));
    }
    return ArrayUtil.toObjectArray(items);
  }

  private boolean processContributorForName(@NotNull ChooseByNameContributor contributor,
                                            @NotNull String name,
                                            @NotNull FindSymbolParameters parameters,
                                            @NotNull ProgressIndicator canceled,
                                            @NotNull List<? super NavigationItem> items) {
    if (myProject.isDisposed()) {
      return true;
    }
    try {
      boolean searchInLibraries = parameters.isSearchInLibraries();
      long start = System.nanoTime();
      int[] count = {0};

      if (contributor instanceof ChooseByNameContributorEx) {
        ((ChooseByNameContributorEx)contributor).processElementsWithName(name, item -> {
          canceled.checkCanceled();
          count[0]++;
          if (acceptItem(item)) items.add(item);
          return true;
        }, parameters);

        if (LOG.isDebugEnabled()) {
          LOG.debug(TimeoutUtil.getDurationMillis(start) + "," + contributor + "," + count[0]);
        }
      }
      else {
        NavigationItem[] itemsByName = contributor.getItemsByName(name, parameters.getLocalPatternName(), myProject, searchInLibraries);
        count[0] += itemsByName.length;
        for (NavigationItem item : itemsByName) {
          canceled.checkCanceled();
          if (item == null) {
            PluginException.logPluginError(LOG, "null item from contributor " + contributor + " for name " + name, null, contributor.getClass());
            continue;
          }
          VirtualFile file = item instanceof PsiElement && !(item instanceof PomTargetPsiElement)
                             ? PsiUtilCore.getVirtualFile((PsiElement)item) : null;
          if (file != null && !parameters.getSearchScope().contains(file)) continue;

          if (acceptItem(item)) {
            items.add(item);
          }
        }
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug(TimeoutUtil.getDurationMillis(start) + "," + contributor + "," + count[0]);
      }
    }
    catch (ProcessCanceledException ex) {
      // index corruption detected, ignore
    }
    catch (Throwable ex) {
      LOG.error(ex);
    }
    return true;
  }

  /**
   * Get elements by name from contributors.
   *
   * @param name a name
   * @param checkBoxState if {@code true}, non-project files are considered as well
   * @param pattern a pattern to use
   * @return a array of navigation items from contributors for
   *  which {@link #acceptItem(NavigationItem)} returns {@code true}.
   */
  @Override
  public Object @NotNull [] getElementsByName(final @NotNull String name, final boolean checkBoxState, final @NotNull String pattern) {
    return getElementsByName(name, FindSymbolParameters.wrap(pattern, myProject, checkBoxState), new ProgressIndicatorBase());
  }

  @Override
  public String getElementName(@NotNull Object element) {
    if (!(element instanceof NavigationItem)) {
      throw new AssertionError(element + " of " + element.getClass() + " in " + this + " of " + getClass());
    }
    return ((NavigationItem)element).getName();
  }

  @Override
  public String getHelpId() {
    return null;
  }

  protected List<ChooseByNameContributor> getContributorList() {
    return myContributors;
  }

  protected ChooseByNameContributor[] getContributors() {
    return getContributorList().toArray(new ChooseByNameContributor[]{});
  }

  /**
   * This method allows extending classes to introduce additional filtering criteria to model
   * beyond pattern and project/non-project files. The default implementation just returns true.
   *
   * @param item an item to filter
   * @return true if the item is acceptable according to additional filtering criteria.
   */
  protected boolean acceptItem(NavigationItem item) {
    return true;
  }

  @Override
  public boolean useMiddleMatching() {
    return true;
  }

  public @NotNull String removeModelSpecificMarkup(@NotNull String pattern) {
    return pattern;
  }

  public @NotNull Project getProject() {
    return myProject;
  }
}
