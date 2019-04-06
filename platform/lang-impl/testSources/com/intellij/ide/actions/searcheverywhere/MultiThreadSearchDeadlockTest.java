// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author mikhail.sokolov
 */
public class MultiThreadSearchDeadlockTest extends LightPlatformCodeInsightFixtureTestCase {

  private static final Collection<SEResultsEqualityProvider> ourEqualityProviders = Collections.singleton(new TrivialElementsEqualityProvider());

  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  @Override
  protected void invokeTestRunnable(@NotNull Runnable runnable) {
    runnable.run();
  }

  public void testDeadlocks() {
    Map<SearchEverywhereContributor<?>, Integer> contributorsMap = new HashMap<>();
    contributorsMap.put(new ReadActionContributor<>("readAction1", 0, 150, Arrays.asList("ri11", "ri12", "ri13", "ri14", "ri15", "ri16")), 10);
    contributorsMap.put(new ReadActionContributor<>("readAction2", 100, 100, Arrays.asList("ri21", "ri22", "ri23", "ri24", "ri25")), 10);
    contributorsMap.put(new WriteActionContributor<>("writeAction1", 300, 50, Arrays.asList("wi11", "wi12", "wi13", "wi14")), 10);

    Collector collector = new Collector();
    Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, getTestRootDisposable());
    MultiThreadSearcher searcher = new MultiThreadSearcher(collector, command -> alarm.addRequest(command, 0), ourEqualityProviders);
    ProgressIndicator progressIndicator = searcher.search(contributorsMap, "tst", false, ignrd -> null);

    try {
      if (!collector.awaitFinish(4000)) {
        Assert.fail("Searching still haven't finished. Possible deadlock");
      }
      Assert.assertEquals(Arrays.asList("ri11", "ri12", "ri13", "ri14", "ri15", "ri16"), collector.getFoundItems("readAction1"));
      Assert.assertEquals(Arrays.asList("ri21", "ri22", "ri23", "ri24", "ri25"), collector.getFoundItems("readAction2"));
      Assert.assertEquals(Arrays.asList("wi11", "wi12", "wi13", "wi14"), collector.getFoundItems("writeAction1"));
    }
    catch (InterruptedException ignored) {
    }
    finally {
      progressIndicator.cancel();
    }
  }

  public void testWriteActionPriority() {
    Map<SearchEverywhereContributor<?>, Integer> contributorsMap = new HashMap<>();
    ReadActionContributor<Object> action1 = new ReadActionContributor<>("readAction1", 100, 150, Arrays.asList("ri11", "ri12", "ri13", "ri14", "ri15", "ri16"));
    ReadActionContributor<Object> action2 = new ReadActionContributor<>("readAction2", 500, 100, Arrays.asList("ri21", "ri22", "ri23", "ri24"));
    contributorsMap.put(action1, 10);
    contributorsMap.put(action2, 10);

    Collector collector = new Collector();
    Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, getTestRootDisposable());
    MultiThreadSearcher searcher = new MultiThreadSearcher(collector, command -> alarm.addRequest(command, 0), ourEqualityProviders);
    ProgressIndicator progressIndicator = searcher.search(contributorsMap, "tst", false, ignrd -> null);

    try {
      Application application = ApplicationManager.getApplication();
      Thread.sleep(400);
      application.invokeLater(() -> WriteAction.run(() -> {}));
      Thread.sleep(900);
      application.invokeLater(() -> WriteAction.run(() -> {}));

      if (!collector.awaitFinish(4000)) {
        Assert.fail("Searching still haven't finished. Possible deadlock");
      }
      Assert.assertEquals(Arrays.asList("ri11", "ri12", "ri13", "ri14", "ri15", "ri16"), collector.getFoundItems("readAction1"));
      Assert.assertEquals(Arrays.asList("ri21", "ri22", "ri23", "ri24"), collector.getFoundItems("readAction2"));
      Assert.assertEquals(3, action1.getAttemptsCount());
      Assert.assertEquals(1, action2.getAttemptsCount());
    }
    catch (InterruptedException ignored) {
    }
    finally {
      progressIndicator.cancel();
    }
  }

  public void testCancelOnWaiting() {
    Map<SearchEverywhereContributor<?>, Integer> contributorsMap = new HashMap<>();
    contributorsMap.put(new ReadActionContributor<>("readAction1", 0, 0, Arrays.asList("ri11", "ri12", "ri13", "ri14", "ri15", "ri16")), 5);
    contributorsMap.put(new WriteActionContributor<>("writeAction1", 500, 0, Arrays.asList("wi11", "wi12", "wi13", "wi14")), 5);

    Collector collector = new Collector();
    Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, getTestRootDisposable());
    MultiThreadSearcher searcher = new MultiThreadSearcher(collector, command -> alarm.addRequest(command, 0), ourEqualityProviders);
    ProgressIndicator progressIndicator = searcher.search(contributorsMap, "tst", false, ignrd -> null);

    try {
      if (!collector.awaitFinish(4000)) {
        Assert.fail("Searching still haven't finished. Possible deadlock");
      }
      Assert.assertEquals(Arrays.asList("ri11", "ri12", "ri13", "ri14", "ri15"), collector.getFoundItems("readAction1"));
      Assert.assertEquals(Arrays.asList("wi11", "wi12", "wi13", "wi14"), collector.getFoundItems("writeAction1"));
    }
    catch (InterruptedException ignored) {
    }
    finally {
      progressIndicator.cancel();
    }
  }

  private static class Collector implements SESearcher.Listener {
    private final Map<String, List<Object>> resultsMap = new ConcurrentHashMap<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void elementsAdded(@NotNull List<SearchEverywhereFoundElementInfo> list) {
      list.forEach(info -> {
        List<Object> section = resultsMap.computeIfAbsent(info.getContributor().getSearchProviderId(), s -> new ArrayList<>());
        section.add(info.getElement());
      });
    }

    @Override
    public void elementsRemoved(@NotNull List<SearchEverywhereFoundElementInfo> list) {
      list.forEach(info -> {
        List<Object> section = resultsMap.computeIfAbsent(info.getContributor().getSearchProviderId(), s -> new ArrayList<>());
        section.remove(info.getElement());
      });
    }

    @Override
    public void searchFinished(@NotNull Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors) {
      latch.countDown();
    }

    public List<Object> getFoundItems(String contributorID) {
      return resultsMap.get(contributorID);
    }

    public boolean awaitFinish(long millis) throws InterruptedException {
      return latch.await(millis, TimeUnit.MILLISECONDS);
    }
  }

  private static abstract class TestContributor<T> implements SearchEverywhereContributor<T> {
    protected final String name;
    protected final long initDelay;
    protected final long eachItemDelay;
    protected final List<Object> items;

    protected TestContributor(String name, long initDelay, long eachItemDelay, List<Object> items) {
      this.name = name;
      this.initDelay = initDelay;
      this.eachItemDelay = eachItemDelay;
      this.items = items;
    }

    @NotNull
    @Override
    public String getSearchProviderId() {
      return name;
    }

    @NotNull
    @Override
    public String getGroupName() {
      return name;
    }

    @Nullable
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
    public boolean processSelectedItem(@NotNull Object selected, int modifiers, @NotNull String searchText) {
      return false;
    }

    @Nullable
    @Override
    public Object getDataForItem(@NotNull Object element, @NotNull String dataId) {
      return null;
    }

    @NotNull
    @Override
    public ListCellRenderer getElementsRenderer(@NotNull JList list) {
      return new DefaultListCellRenderer();
    }
  }

  private static class ReadActionContributor<T> extends TestContributor<T> {

    private int count = 0;

    protected ReadActionContributor(String name, long initDelay, long eachItemDelay, List<Object> items) {
      super(name, initDelay, eachItemDelay, items);
    }

    @Override
    public void fetchElements(@NotNull String pattern,
                              boolean everywhere,
                              @Nullable SearchEverywhereContributorFilter<T> filter,
                              @NotNull ProgressIndicator progressIndicator,
                              @NotNull Function<Object, Boolean> consumer) {
      try {
        Thread.sleep(initDelay);
      }
      catch (InterruptedException ignored) {
      }

      ProgressIndicatorUtils.yieldToPendingWriteActions();
      ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(() -> {
        count++;
        try {
          for (Object item : items) {
            progressIndicator.checkCanceled();
            consumer.apply(item);
            Thread.sleep(eachItemDelay);
          }
        }
        catch (InterruptedException ignored) {
        }
      }, progressIndicator);
    }

    private int getAttemptsCount() {
      return count;
    }
  }

  private static class WriteActionContributor<T> extends TestContributor<T> {

    protected WriteActionContributor(String name, long initDelay, long eachItemDelay, List<Object> items) {
      super(name, initDelay, eachItemDelay, items);
    }

    @Override
    public void fetchElements(@NotNull String pattern,
                              boolean everywhere,
                              @Nullable SearchEverywhereContributorFilter<T> filter,
                              @NotNull ProgressIndicator progressIndicator,
                              @NotNull Function<Object, Boolean> consumer) {
      try {
        Thread.sleep(initDelay);
        for (Object item : items) {
          ApplicationManager.getApplication().invokeAndWait(() -> WriteAction.run(() -> {}));
          consumer.apply(item);
          Thread.sleep(eachItemDelay);
        }
      }
      catch (InterruptedException ignored) {
      }
    }
  }
}
