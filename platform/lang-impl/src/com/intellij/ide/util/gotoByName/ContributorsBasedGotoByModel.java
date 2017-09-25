/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util.gotoByName;

import com.intellij.concurrency.JobLauncher;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.util.NavigationItemListCellRenderer;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * Contributor-based goto model
 */
public abstract class ContributorsBasedGotoByModel implements ChooseByNameModelEx {
  public static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.gotoByName.ContributorsBasedGotoByModel");

  protected final Project myProject;
  private final ChooseByNameContributor[] myContributors;

  protected ContributorsBasedGotoByModel(@NotNull Project project, @NotNull ChooseByNameContributor[] contributors) {
    myProject = project;
    myContributors = contributors;
    assert !Arrays.asList(contributors).contains(null);
  }

  @Override
  public ListCellRenderer getListCellRenderer() {
    return new NavigationItemListCellRenderer();
  }

  public boolean sameNamesForProjectAndLibraries() {
    return !ChooseByNameBase.ourLoadNamesEachTime;
  }

  private final ConcurrentMap<ChooseByNameContributor, TIntHashSet> myContributorToItsSymbolsMap = ContainerUtil.newConcurrentMap();
  private volatile IdFilter myIdFilter;
  private volatile boolean myIdFilterForLibraries;

  @Override
  public void processNames(final Processor<String> nameProcessor, final boolean checkBoxState) {
    long start = System.currentTimeMillis();
    List<ChooseByNameContributor> liveContribs = filterDumb(myContributors);
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    Processor<ChooseByNameContributor> processor = new ReadActionProcessor<ChooseByNameContributor>() {
      @Override
      public boolean processInReadAction(@NotNull ChooseByNameContributor contributor) {
        try {
          if (!myProject.isDisposed()) {
            long contributorStarted = System.currentTimeMillis();
            final TIntHashSet filter = new TIntHashSet(1000);
            myContributorToItsSymbolsMap.put(contributor, filter);
            if (contributor instanceof ChooseByNameContributorEx) {
              ((ChooseByNameContributorEx)contributor).processNames(s -> {
                if (nameProcessor.process(s)) {
                  filter.add(s.hashCode());
                }
                return true;
              }, FindSymbolParameters.searchScopeFor(myProject, checkBoxState), getIdFilter(checkBoxState));
            } else {
              String[] names = contributor.getNames(myProject, checkBoxState);
              for (String element : names) {
                if (nameProcessor.process(element)) {
                  filter.add(element.hashCode());
                }
              }
            }

            if (LOG.isDebugEnabled()) {
              LOG.debug(contributor + " for " + (System.currentTimeMillis() - contributorStarted));
            }
          }
        }
        catch (ProcessCanceledException | IndexNotReadyException ex) {
          // index corruption detected, ignore
        }
        catch (Exception ex) {
          LOG.error(ex);
        }
        return true;
      }
    };
    if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(liveContribs, indicator, true, processor)) {
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

  IdFilter getIdFilter(boolean withLibraries) {
    IdFilter idFilter = myIdFilter;

    if (idFilter == null || myIdFilterForLibraries != withLibraries) {
      idFilter = IdFilter.getProjectIdFilter(myProject, withLibraries);
      myIdFilter = idFilter;
      myIdFilterForLibraries = withLibraries;
    }
    return idFilter;
  }

  @NotNull
  @Override
  public String[] getNames(final boolean checkBoxState) {
    final THashSet<String> allNames = ContainerUtil.newTroveSet();

    Collection<String> result = Collections.synchronizedCollection(allNames);
    processNames(Processors.cancelableCollectProcessor(result), checkBoxState);
    if (LOG.isDebugEnabled()) {
      LOG.debug("getNames(): (got "+allNames.size()+" elements)");
    }
    return ArrayUtil.toStringArray(allNames);
  }

  private List<ChooseByNameContributor> filterDumb(ChooseByNameContributor[] contributors) {
    if (!DumbService.getInstance(myProject).isDumb()) return Arrays.asList(contributors);
    List<ChooseByNameContributor> answer = new ArrayList<>(contributors.length);
    for (ChooseByNameContributor contributor : contributors) {
      if (DumbService.isDumbAware(contributor)) {
        answer.add(contributor);
      }
    }

    return answer;
  }

  @NotNull
  public Object[] getElementsByName(@NotNull final String name,
                                    @NotNull final FindSymbolParameters parameters,
                                    @NotNull final ProgressIndicator canceled) {
    long elementByNameStarted = System.currentTimeMillis();
    final List<NavigationItem> items = Collections.synchronizedList(new ArrayList<NavigationItem>());

    Processor<ChooseByNameContributor> processor = contributor -> {
      if (myProject.isDisposed()) {
        return true;
      }
      TIntHashSet filter = myContributorToItsSymbolsMap.get(contributor);
      if (filter != null && !filter.contains(name.hashCode())) return true;
      try {
        boolean searchInLibraries = parameters.getSearchScope().isSearchInLibraries();
        long contributorStarted = System.currentTimeMillis();

        if (contributor instanceof ChooseByNameContributorEx) {
          ((ChooseByNameContributorEx)contributor).processElementsWithName(name, item -> {
            canceled.checkCanceled();
            if (acceptItem(item)) items.add(item);
            return true;
          }, parameters);

          if (LOG.isDebugEnabled()) {
            LOG.debug(System.currentTimeMillis() - contributorStarted + "," + contributor + ",");
          }
        } else {
          NavigationItem[] itemsByName = contributor.getItemsByName(name, parameters.getLocalPatternName(), myProject, searchInLibraries);
          for (NavigationItem item : itemsByName) {
            canceled.checkCanceled();
            if (item == null) {
              PluginId pluginId = PluginManager.getPluginByClassName(contributor.getClass().getName());
              if (pluginId != null) {
                LOG.error(new PluginException("null item from contributor " + contributor + " for name " + name, pluginId));
              }
              else {
                LOG.error("null item from contributor " + contributor + " for name " + name);
              }
              continue;
            }

            if (acceptItem(item)) {
              items.add(item);
            }
          }

          if (LOG.isDebugEnabled()) {
            LOG.debug(System.currentTimeMillis() - contributorStarted + "," + contributor + "," + itemsByName.length);
          }
        }
      }
      catch (ProcessCanceledException ex) {
        // index corruption detected, ignore
      }
      catch (Exception ex) {
        LOG.error(ex);
      }
      return true;
    };
    if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(filterDumb(myContributors), canceled, true, processor)) {
      canceled.cancel();
    }
    canceled.checkCanceled(); // if parallel job execution was canceled because of PCE, rethrow it from here
    if (LOG.isDebugEnabled()) {
      LOG.debug("Retrieving " + name + ":" + items.size() + " for " + (System.currentTimeMillis() - elementByNameStarted));
    }
    return ArrayUtil.toObjectArray(items);
  }

  /**
   * Get elements by name from contributors.
   *
   * @param name a name
   * @param checkBoxState if true, non-project files are considered as well
   * @param pattern a pattern to use
   * @return a list of navigation items from contributors for
   *  which {@link #acceptItem(NavigationItem) returns true.
   *
   */
  @NotNull
  @Override
  public Object[] getElementsByName(final String name, final boolean checkBoxState, final String pattern) {
    return getElementsByName(name, FindSymbolParameters.wrap(pattern, myProject, checkBoxState), new ProgressIndicatorBase());
  }

  @Override
  public String getElementName(Object element) {
    if (!(element instanceof NavigationItem)) {
      throw new AssertionError((element == null ? "null" : element + " of " + element.getClass()) + " in " + this + " of " + getClass());
    }
    return ((NavigationItem)element).getName();
  }

  @Override
  public String getHelpId() {
    return null;
  }

  protected ChooseByNameContributor[] getContributors() {
    return myContributors;
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
}
