// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * @author mikhail.sokolov
 */
public class MultithreadSearchTest extends LightPlatformCodeInsightFixtureTestCase {

  public static final String MORE_ITEM = "...MORE";

  public void testWithoutCollisions() {
    Collection<SearchEverywhereContributor<?>> contributors = Arrays.asList(
      createTestContributor("test1", "item1_1", "item1_2", "item1_3", "item1_4", "item1_5", "item1_6", "item1_7", "item1_8", 
                            "item1_9", "item1_10", "item1_11", "item1_12", "item1_13", "item1_14", "item1_15"),
      createTestContributor("test2", "item2_1", "item2_2", "item2_3", "item2_4", "item2_5", "item2_6", "item2_7", "item2_8",
                            "item2_9", "item2_10", "item2_11", "item2_12"),
      createTestContributor("test3", "item3_1", "item3_2", "item3_3", "item3_4", "item3_5", "item3_6", "item3_7", "item3_8"),
      createTestContributor("test4", "item4_1", "item4_2", "item4_3", "item4_4", "item4_5", "item4_6", "item4_7", "item4_8",
                            "item4_9", "item4_10", "item4_11", "item4_12", "item4_13"),
      createTestContributor("test5"),
      createTestContributor("test6", "item6_1", "item6_2", "item6_3", "item6_4", "item6_5", "item6_6", "item6_7", "item6_8",
                            "item6_9", "item6_10", "item6_11", "item6_12", "item6_13"),
      createTestContributor("test7", "item7_1", "item7_2", "item7_3", "item7_4", "item7_5", "item7_6", "item7_7", "item7_8",
                            "item7_9", "item7_10"),
      createTestContributor("test8", "item8_1", "item8_2", "item8_3", "item8_4", "item8_5")
    );

    SearchResultsCollector collector = new SearchResultsCollector();
    MultithreadSearcher searcher = new MultithreadSearcher(collector);
    searcher.search(contributors, null, false, ignrd -> null, 10);

    try {
      collector.awaitFinish();
      checkResult(collector, "test1", 10, true);
      checkResult(collector, "test2", 10, true);
      checkResult(collector, "test3", 8, false);
      checkResult(collector, "test4", 10, true);
      checkResult(collector, "test5", 0, false);
      checkResult(collector, "test6", 10, true);
      checkResult(collector, "test7", 10, false);
      checkResult(collector, "test8", 5, false);
    }
    catch (InterruptedException e) {
      Assert.fail("Thread was unexpectedly interrupted");
    }
  }

  private static void checkResult(SearchResultsCollector collector, String contributorId, int expectedElements, boolean moreItemExpected) {
    List<String> values = collector.getContributorValues(contributorId);
    if (moreItemExpected) {
      expectedElements += 1;
    }

    Assert.assertEquals("found elements in contributor " + contributorId, expectedElements, values.size());
    if (moreItemExpected) {
      Assert.assertEquals("'MORE' item in results for contributor " + contributorId, MORE_ITEM, values.get(values.size() - 1));
    } else {
      Assert.assertFalse("no 'MORE' item in results for contributor " + contributorId, values.contains(MORE_ITEM));
    }
  }

  private static SearchEverywhereContributor<?> createTestContributor(String id, String... items) {
    return new SearchEverywhereContributor<Object>() {
      @NotNull
      @Override
      public String getSearchProviderId() {
        return id;
      }

      @NotNull
      @Override
      public String getGroupName() {
        return id;
      }

      @Override
      public String includeNonProjectItemsText() {
        return null;
      }

      @Override
      public int getSortWeight() {
        return 0;
      }

      @Override
      public boolean showInFindResults() {
        return false;
      }

      @Override
      public void fetchElements(String pattern,
                                boolean everywhere,
                                SearchEverywhereContributorFilter<Object> filter,
                                ProgressIndicator progressIndicator,
                                Function<Object, Boolean> consumer) {
        boolean flag = true;
        Iterator<String> iterator = Arrays.asList(items).iterator();
        while (flag && iterator.hasNext()) {
          String next = iterator.next();
          flag = consumer.apply(next);
        }
      }

      @Override
      public ContributorSearchResult<Object> search(String pattern,
                                                    boolean everywhere,
                                                    SearchEverywhereContributorFilter<Object> filter,
                                                    ProgressIndicator progressIndicator,
                                                    int elementsLimit) {
        return ContributorSearchResult.empty();
      }

      @Override
      public boolean processSelectedItem(Object selected, int modifiers, String searchText) {
        return false;
      }

      @Override
      public ListCellRenderer getElementsRenderer(JList<?> list) {
        return null;
      }

      @Override
      public Object getDataForItem(Object element, String dataId) {
        return null;
      }
    };
  }

  private static class SearchResultsCollector implements MultithreadSearcher.Listener {

    private final Map<String, List<String>> myMap = new ConcurrentHashMap<>();
    private final AtomicBoolean myFinished = new AtomicBoolean(false);
    private final CountDownLatch myLatch = new CountDownLatch(1);

    public List<String> getContributorValues(String contributorId) {
      List<String> values = myMap.get(contributorId);
      return values != null ? values : Collections.emptyList();
    }

    public void awaitFinish() throws InterruptedException {
      myLatch.await();
    }

    @Override
    public void elementsAdded(List<MultithreadSearcher.ElementInfo> added) {
      added.forEach(info -> {
        List<String> list = myMap.computeIfAbsent(info.getContributor().getSearchProviderId(), s -> new ArrayList<>());
        list.add((String) info.getElement());
      });
    }

    @Override
    public void elementsRemoved(List<MultithreadSearcher.ElementInfo> removed) {
      removed.forEach(info -> {
        List<String> list = myMap.get(info.getContributor().getSearchProviderId());
        Assert.assertNotNull("Trying to remove object, that wasn't added", list);
        list.remove(info.getElement());
      });
    }

    @Override
    public void searchFinished(Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors) {
      hasMoreContributors.forEach((contributor, aBoolean) -> {
        List<String> list = myMap.get(contributor.getSearchProviderId());
        Assert.assertNotNull("If section has MORE item it cannot be empty", list);
        list.add(MORE_ITEM);
      });

      boolean set = myFinished.compareAndSet(false, true);
      Assert.assertTrue("More than one finish event", set);

      myLatch.countDown();
    }
  }
}
