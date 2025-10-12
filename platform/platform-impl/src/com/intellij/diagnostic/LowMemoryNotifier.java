// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.diagnostic.VMOptions.MemoryKind;
import com.intellij.diagnostic.hprof.action.HeapDumpSnapshotRunnable;
import com.intellij.diagnostic.report.MemoryReportReason;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.containers.ContainerUtil;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.Meter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;

import static com.intellij.openapi.util.LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC;
import static com.intellij.platform.diagnostic.telemetry.PlatformScopesKt.PlatformMetrics;
import static com.intellij.util.SystemProperties.getFloatProperty;
import static com.intellij.util.SystemProperties.getLongProperty;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Asks user to increase IDE heap size if significant memory pressure is detected.
 * <p>
 * Detects memory pressure by subscribing to {@link LowMemoryWatcher}, and throttles and averaging its events over a larger
 * time period, to separate a long-term memory deficit.
 */
@ApiStatus.Internal
@VisibleForTesting
public final class LowMemoryNotifier implements Disposable {
  private static final Logger LOG = Logger.getInstance(LowMemoryNotifier.class);

  private static final Set<MemoryKind> notifications = ConcurrentCollectionFactory.createConcurrentSet();

  //@formatter:off
  private static final long SUMMARISING_WINDOW_MS = getLongProperty("LowMemoryNotifier.SUMMARISING_WINDOW_MS", MINUTES.toMillis(15));
  private static final long THROTTLING_PERIOD_MS = getLongProperty("LowMemoryNotifier.THROTTLING_PERIOD_MS", SECONDS.toMillis(20));
  private static final double LONG_TERM_MEMORY_DEFICIT_THRESHOLD = getFloatProperty("LowMemoryNotifier.LONG_TERM_MEMORY_DEFICIT_THRESHOLD", 5);
  //@formatter:on

  private final ThrottlingWindowedFilter throttlingFilter = new ThrottlingWindowedFilter(
    SUMMARISING_WINDOW_MS,
    THROTTLING_PERIOD_MS
  );

  private final LowMemoryWatcher watcher;

  LowMemoryNotifier() {
    Meter otelMeter = TelemetryManager.getInstance().getMeter(PlatformMetrics);
    DoubleGauge memoryDeficitScoreMetric = otelMeter.gaugeBuilder("LowMemory.memoryDeficitScore").build();

    watcher = LowMemoryWatcher.register(
      () -> {
        //We use low-memory notification to form a recommendation for the user to extend HEAP.
        //The problem is: raw low-memory notifications could come frequently, if a short-but-intensive spike of activity is
        // underway -- like indexing, or huge find usage, etc -- but such short spikes of low-memory conditions are not enough
        // reason to annoy user to extend the heap. Only _regular_, relatively _long-term_ signs of a memory deficit are enough
        // of a reason to extend the heap.
        // Hence, we _throttle_ the raw signals (THROTTLING_PERIOD_MS) and sum the throttled signals over last (SUMMARISING_WINDOW_MS).
        // I.e. the maximum sum = SUMMARISING_WINDOW_MS/THROTTLING_PERIOD_MS.
        // If there are > LONG_TERM_MEMORY_DEFICIT_THRESHOLD throttled low-memory signals in a SUMMARISING_WINDOW_MS -- only when
        // we recommend the user extending the heap.
        double memoryDeficitScore = throttlingFilter.throttledSum(System.currentTimeMillis());
        memoryDeficitScoreMetric.set(memoryDeficitScore);

        LOG.info("Memory deficit score: " + memoryDeficitScore + ", threshold: " + LONG_TERM_MEMORY_DEFICIT_THRESHOLD);
        if (memoryDeficitScore > LONG_TERM_MEMORY_DEFICIT_THRESHOLD) {
          throttlingFilter.reset();
          showNotification(MemoryKind.HEAP, false);
        }
      },
      ONLY_AFTER_GC
    );
  }

  @Override
  public void dispose() {
    watcher.stop();
  }

  static void showNotificationFromCrashAnalysis() {
    showNotification(MemoryKind.HEAP, true, true);
  }

  static void showNotification(@NotNull MemoryKind kind, boolean oomError) {
    showNotification(kind, oomError, false);
  }

  private static void showNotification(@NotNull MemoryKind kind, boolean oomError, boolean fromCrashReport) {
    int currentXmx = VMOptions.readOption(MemoryKind.HEAP, true);
    var projects = ProjectManager.getInstance().getOpenProjects();
    int projectCount = projects.length;
    boolean isDumb = ContainerUtil.exists(projects, p -> DumbService.isDumb(p));
    UILatencyLogger.lowMemory(kind, currentXmx, projectCount, oomError, fromCrashReport, isDumb);

    if (isDumb
        && kind == MemoryKind.HEAP
        && !oomError
        && !fromCrashReport
        && Registry.is("ide.suppress.low.memory.notifications.when.dumb")) {
      LOG.info("Skipped Low Memory notifications because indexing is in progress");
      return;
    }

    if (!notifications.add(kind)) return;

    var message =
      oomError ? IdeBundle.message("low.memory.notification.error", kind.label()) : IdeBundle.message("low.memory.notification.warning");
    var type = oomError ? NotificationType.ERROR : NotificationType.WARNING;
    var notification = new Notification("Low Memory", IdeBundle.message("low.memory.notification.title"), message, type);

    if (!fromCrashReport) {
      notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("low.memory.notification.analyze.action"), () ->
        new HeapDumpSnapshotRunnable(MemoryReportReason.UserInvoked,
                                     HeapDumpSnapshotRunnable.AnalysisOption.SCHEDULE_ON_NEXT_START).run()));
    }

    if (VMOptions.canWriteOptions()) {
      notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("low.memory.notification.action"),
                                                                     () -> new EditMemorySettingsDialog(kind).show()));
    }

    notification.whenExpired(() -> notifications.remove(kind));

    Notifications.Bus.notify(notification);
  }

  /**
   * 'Filter' here is used in 'signal processing' sense, not usual software-engineering sense.
   * The class extracts the signals that are 'regular', i.e. more-or-less evenly spaced.
   * More specifically, the class:
   * 1. Throttles out too close subsequent signals: all the signals that come in {@link #throttlingPeriodMs} are counted as 1
   * 2. Sums up all the (throttled) signals inside {@link #windowSizeMs}.
   * This way short bursts of signals are dampened, but signals that are more evenly spaced are not.
   */
  @VisibleForTesting
  @ApiStatus.Internal
  public static class ThrottlingWindowedFilter {
    private final long windowSizeMs;
    private final long throttlingPeriodMs;

    private long recentUpdateTimestampMs = 0;
    private final Queue<DataPoint> history = new ArrayDeque<>();

    public ThrottlingWindowedFilter(long windowSizeMs,
                                    long throttlingPeriodMs) {
      this.windowSizeMs = windowSizeMs;
      this.throttlingPeriodMs = throttlingPeriodMs;
    }

    public synchronized double throttledSum(long currentTimeMs) {
      if (history.isEmpty()
          || currentTimeMs - recentUpdateTimestampMs > throttlingPeriodMs) {

        history.offer(new DataPoint(currentTimeMs, /*value:*/ 1));
        recentUpdateTimestampMs = currentTimeMs;

        //drop points older than windowSize:
        while (!history.isEmpty() && history.peek().timestamp < currentTimeMs - windowSizeMs) {
          history.poll();
        }
      }//else: throttle the update out

      return history.stream()
        .mapToLong(period -> period.value)
        .sum();
    }

    public synchronized void reset() {
      history.clear();
    }

    private record DataPoint(long timestamp, long value) {
    }
  }
}
