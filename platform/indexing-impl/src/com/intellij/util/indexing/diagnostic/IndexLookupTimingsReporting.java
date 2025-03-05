// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.project.Project;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.MathUtil;
import com.intellij.util.indexing.IndexId;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.platform.diagnostic.telemetry.PlatformScopesKt.Indexes;
import static com.intellij.util.SystemProperties.getBooleanProperty;
import static com.intellij.util.SystemProperties.getIntProperty;
import static com.intellij.util.indexing.diagnostic.IndexLookupTimingsReporting.IndexOperationAggregatesCollector.MAX_TRACKABLE_DURATION_MS;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toSet;

/**
 * Index lookup timings: various stats and reporting.
 * Basically: there are detailed (per lookup) and aggregated (mean, %-iles, per minute) timings, which
 * could be reported to FUS, and/or OpenTelemetry.
 * <p/>
 * Be cautious with enabling detailed (per lookup) reporting: index lookup is one of the most frequent calls
 * in the platform API, hence detailed (per lookup) reporting produces quite a lot of data.
 */
@Internal
public final class IndexLookupTimingsReporting {
  private static final Logger LOG = Logger.getInstance(IndexLookupTimingsReporting.class);
  private static final ThrottledLogger THROTTLED_LOG = new ThrottledLogger(LOG, SECONDS.toMillis(10));

  private static final IJTracer OTEL_TRACER = TelemetryManager.getInstance().getTracer(Indexes);

  /**
   * 'Feature flag': report individual index lookup events to FUS.
   * Default: true for EAP, false otherwise
   * See {@link #REPORT_TO_FUS_INDIVIDUAL_LOOKUPS_ONLY_LONGER_THAN_MS}
   */
  private static final boolean REPORT_INDIVIDUAL_LOOKUPS_TO_FUS = getBooleanProperty(
    "IndexLookupTimingsReporting.REPORT_INDIVIDUAL_LOOKUPS_TO_FUS", false
  );

  /**
   * 'Feature flag': report individual index lookup timings as OpenTelemetry.Traces
   * Default: false.
   * Enable with caution, since index lookup is one of the most frequent operations, and
   * enabling its reporting may produce quite a lot of data.
   */
  public static final boolean REPORT_INDIVIDUAL_LOOKUPS_TO_OPEN_TELEMETRY = getBooleanProperty(
    "IndexLookupTimingsReporting.REPORT_INDIVIDUAL_LOOKUPS_TO_OPEN_TELEMETRY",
    false
  );

  //OTel span names:
  private static final String SPAN_NAME_INDEX_LOOKUP_ALL_KEYS = "index lookup: all keys";
  private static final String SPAN_NAME_INDEX_LOOKUP_ENTRIES = "index lookup: file entries";
  private static final String SPAN_NAME_INDEX_LOOKUP_STUBS = "index lookup: stub entries";
  private static final String SPAN_NAME_INDEX_UP_TO_DATE_CHECK = "index up-to-date check";
  private static final String SPAN_NAME_STUB_TREE_DESERIALIZATION = "stub tree deserialization";


  /**
   * Collect aggregated statistics over index lookups: mean, %-iles, lookup counts.
   * Collected stats could be then reported to FUS/OTel, see {@link #REPORT_AGGREGATED_STATS_TO_FUS},
   * {@link #REPORT_AGGREGATED_STATS_TO_OPEN_TELEMETRY}.
   * Beware: if any of REPORT_AGGREGATED_STATS_TO_XXX flags are set -- this flag must be set also.
   */
  private static final boolean COLLECT_AGGREGATED_STATS = getBooleanProperty(
    "IndexLookupTimingsReporting.COLLECT_AGGREGATED_STATS", true
  );

  /**
   * 'Feature flag': report aggregated (mean, %-iles) index lookup statistics to FUS.
   * Default: true
   */
  private static final boolean REPORT_AGGREGATED_STATS_TO_FUS = getBooleanProperty(
    "IndexLookupTimingsReporting.REPORT_AGGREGATED_STATS_TO_FUS", true
  );

  /**
   * Report lookup operation X to analytics only if total duration of the operation X {@code >REPORT_TO_FUS_INDIVIDUAL_LOOKUPS_ONLY_LONGER_THAN_MS}.
   * There are a lot of index lookups, and this threshold allows reducing reporting traffic, since we're really only interested in
   * long operations. Default value 0 means 'report lookups >0ms only'.
   * <p>
   * BEWARE: different values for that parameter correspond to a different way of sampling, hence, in theory, should be treated as
   * different event schema _version_.
   */
  private static final int REPORT_TO_FUS_INDIVIDUAL_LOOKUPS_ONLY_LONGER_THAN_MS = getIntProperty(
    "IndexLookupTimingsReporting.REPORT_TO_FUS_INDIVIDUAL_LOOKUPS_ONLY_LONGER_THAN_MS",
    100
  );

  /**
   * 'Feature flag': report aggregated (mean, %-iles) index lookup statistics to OpenTelemetry.Metrics
   * Default: true
   */
  private static final boolean REPORT_AGGREGATED_STATS_TO_OPEN_TELEMETRY = getBooleanProperty(
    "IndexLookupTimingsReporting.REPORT_AGGREGATED_STATS_TO_OPEN_TELEMETRY", true
  );

  /**
   * How many recursive lookups to allow before suspect it is not a recursive lookup, but
   * just buggy code (missed {@linkplain IndexOperationFusCollector.LookupTraceBase#close()} call) and throw exception
   */
  @VisibleForTesting
  public static final int MAX_LOOKUP_DEPTH = Integer.getInteger("IndexLookup.MAX_LOOKUP_DEPTH", 16);
  /**
   * If true -> throw exception if tracing methods are (likely) called in incorrect order (e.g. .finish() before start(),
   * or .finish() without .start() beforehand).
   * if false (default) -> log warning on incorrect sequence of calls, but try to continue normal operation afterwards.
   * <p>
   */
  @VisibleForTesting
  public static final boolean THROW_ON_INCORRECT_USAGE = getBooleanProperty("IndexLookup.THROW_ON_INCORRECT_USAGE", false);


  /* ================== EVENTS GROUPS: ====================================================== */

  /**
   * Individual lookups
   */
  private static final EventLogGroup INDEX_USAGE_GROUP = new EventLogGroup("index.usage", 3);

  /**
   * Averages/percentiles over lookups in a time window
   */
  private static final EventLogGroup INDEX_USAGE_AGGREGATES_GROUP = new EventLogGroup("index.usage.aggregates", 3);

  /* ================== EVENTS FIELDS: ====================================================== */

  private static final StringEventField FIELD_INDEX_ID = EventFields.StringValidatedByCustomRule("index_id", IndexIdRuleValidator.class);
  private static final BooleanEventField FIELD_LOOKUP_FAILED = EventFields.Boolean("lookup_failed");
  private static final BooleanEventField FIELD_LOOKUP_CANCELLED = EventFields.Boolean("lookup_cancelled");
  /**
   * Total lookup time, as it is seen by 'client' (i.e. including up-to-date/validation, and stubs deserializing, etc...)
   */
  private static final LongEventField FIELD_LOOKUP_DURATION_MS = EventFields.Long("lookup_duration_ms");
  private static final LongEventField FIELD_UP_TO_DATE_CHECK_DURATION_MS = EventFields.Long("up_to_date_check_ms");
  private static final LongEventField FIELD_STUB_TREE_DESERIALIZING_DURATION_MS = EventFields.Long("psi_tree_deserializing_ms");
  /**
   * How many keys were lookup-ed (there are methods to lookup >1 keys at once)
   */
  private static final IntEventField FIELD_LOOKUP_KEYS_COUNT = EventFields.Int("keys");
  /**
   * For cases >1 keys lookup: what operation is applied (AND/OR)
   */
  private static final EnumEventField<LookupOperation> FIELD_LOOKUP_KEYS_OP = EventFields.Enum("lookup_op",
                                                                                               LookupOperation.class,
                                                                                               kind -> kind.name().toLowerCase(Locale.US));
  /**
   * How many keys (approximately) current index contains in total -- kind of 'lookup scale'
   */
  private static final IntEventField FIELD_TOTAL_KEYS_INDEXED_COUNT = EventFields.Int("total_keys_indexed");


  /* ================== EVENTS: ========================================================== */

  enum LookupOperation {AND, OR, UNKNOWN}

  /**
   * Collects and reports performance data (timings mostly) about index usage (lookup). Right now there 'all keys'
   * lookups and 'value(s) by key(s)' lookups are timed and reported with some additional infos.
   * <p>
   * There are 3 type of lookups measured now:
   * <ol>
   *   <li>lookup all keys in index ({@link #EVENT_INDEX_ALL_KEYS_LOOKUP})</li>
   * <li>lookup entries by key(s) from file-based index ({@link #EVENT_INDEX_LOOKUP_ENTRIES_BY_KEYS})</li>
   * <li>lookup entries by key from stub-index ({@link #EVENT_STUB_INDEX_LOOKUP_ENTRIES_BY_KEY})</li>
   * </ol>
   * <p>
   * Lookups have phases which could be worth to time on its own: e.g. 'ensure-up-to-date' phase (there delayed
   * index updates could be applied), and psi-resolve phase (there Stub trees are read and parsed, and lookup key
   * is looked up inside them).
   * <p>
   * In general, for all lookups we report field:
   * <ol>
   *      <li>indexId</li>
   *      <li>total lookup time (from start to finish)</li>
   *      <li>was lookup failed? -- failed lookup is the lookup there index was found to be broken or any other exception
   *      was thrown, not the lookup results in 0 entries found.
   *      <li>approximated size of index (number of keys)</li>
   * </ol>
   * Specific lookups also have their own fields, see apt. _EVENT fields
   */
  public static final class IndexOperationFusCollector extends CounterUsagesCollector {

    private static final VarargEventId EVENT_INDEX_ALL_KEYS_LOOKUP = INDEX_USAGE_GROUP.registerVarargEvent(
      "lookup.all_keys",
      FIELD_INDEX_ID,

      FIELD_LOOKUP_FAILED,
      FIELD_LOOKUP_CANCELLED,

      FIELD_LOOKUP_DURATION_MS,          //LOOKUP_DURATION = (UP_TO_DATE_CHECK_DURATION) + (pure index lookup time)
      FIELD_UP_TO_DATE_CHECK_DURATION_MS,

      FIELD_TOTAL_KEYS_INDEXED_COUNT
      //LOOKUP_RESULT_ENTRIES_COUNT is useless here, since it == TOTAL_KEYS_INDEXED_COUNT
    );

    private static final IntEventField FIELD_LOOKUP_RESULT_ENTRIES_COUNT = EventFields.Int("entries_found");

    private static final VarargEventId EVENT_STUB_INDEX_LOOKUP_ENTRIES_BY_KEY = INDEX_USAGE_GROUP.registerVarargEvent(
      "lookup.stub_entries",
      FIELD_INDEX_ID,

      FIELD_LOOKUP_FAILED,
      FIELD_LOOKUP_CANCELLED,

      //LOOKUP_DURATION = (UP_TO_DATE_CHECK_DURATION) + (pure index lookup time) + (STUB_TREE_DESERIALIZING_DURATION)
      FIELD_LOOKUP_DURATION_MS,
      FIELD_UP_TO_DATE_CHECK_DURATION_MS,
      FIELD_STUB_TREE_DESERIALIZING_DURATION_MS,

      //RC: StubIndex doesn't have methods to lookup >1 keys at once, so LOOKUP_KEYS_COUNT/LOOKUP_KEYS_OP is useless here
      FIELD_TOTAL_KEYS_INDEXED_COUNT,
      FIELD_LOOKUP_RESULT_ENTRIES_COUNT
    );

    private static final VarargEventId EVENT_INDEX_LOOKUP_ENTRIES_BY_KEYS = INDEX_USAGE_GROUP.registerVarargEvent(
      "lookup.entries",
      FIELD_INDEX_ID,

      FIELD_LOOKUP_FAILED,
      FIELD_LOOKUP_CANCELLED,

      FIELD_LOOKUP_DURATION_MS,       //LOOKUP_DURATION = (UP_TO_DATE_CHECK_DURATION) + (pure index lookup time)
      FIELD_UP_TO_DATE_CHECK_DURATION_MS,

      FIELD_LOOKUP_KEYS_COUNT,
      FIELD_LOOKUP_KEYS_OP,
      FIELD_TOTAL_KEYS_INDEXED_COUNT,
      FIELD_LOOKUP_RESULT_ENTRIES_COUNT
    );


    // ================== IMPLEMENTATION METHODS:

    @Override
    public EventLogGroup getGroup() {
      return INDEX_USAGE_GROUP;
    }

    //========================== CLASSES:

    protected abstract static class LookupTraceBase<T extends LookupTraceBase<T>> implements AutoCloseable,
                                                                                             Cloneable {
      /**
       * In case of re-entrant lookup (i.e. lookup invoked inside another lookup's callback) this field
       * links to a trace of lookup above current one on the callstack. This way 'traces' form a (linked)
       * stack, with top of the stack sitting in {@linkplain #currentTraceHolder}
       */
      protected T parentTrace = null;
      /**
       * depth of current lookup trace object, 0 for top-level lookup trace
       * -1 for un-initialized trace, before {@link #lookupStarted(IndexId)} call
       */
      protected int depth = -1;
      protected final ThreadLocal<T> currentTraceHolder;

      protected @Nullable IndexId<?, ?> indexId;

      protected long lookupStartedAtMs;
      protected boolean lookupFailed;
      protected boolean lookupCancelled;
      protected int totalKeysIndexed;
      protected int lookupResultSize;

      protected LookupTraceBase(ThreadLocal<T> current) {
        this.currentTraceHolder = current;
      }

      protected T lookupStarted(final @NotNull IndexId<?, ?> indexId) {
        if (depth > MAX_LOOKUP_DEPTH) {
          logOrThrowMisuse();
          //Even if !THROW_ON_INCORRECT_USAGE -> still do not allow to unwind traces deeper than MAX_LOOKUP_DEPTH, as
          // this is a risk of OoM. Instead, just overwrite current trace fields with new values (compromises stats
          // correctness, yes)
        }
        else if (depth >= 0 /* && depth <= MAX_LOOKUP_DEPTH */) {
          //Since MAX_DEPTH is limited, we could pre-allocate MAX_DEPTH trace instances in advance, and keep them
          // in array, thus avoiding allocation altogether. But I didn't go this route because re-entrant lookups are
          // considered a suspicious case: it is either misuse of index API, or a valid but unexpected use, and
          // optimize for either of them seems to be premature
          final T childTrace = this.clone();
          currentTraceHolder.set(childTrace);

          childTrace.setupTraceBeforeStart(indexId, /* parent = */ typeSafeThis());
          return childTrace;
        }

        // if (depth < 0)
        setupTraceBeforeStart(indexId, /* parent = */ null);
        return typeSafeThis();
      }

      protected void setupTraceBeforeStart(final @NotNull IndexId<?, ?> indexId,
                                           final @Nullable T parentTrace) {
        this.indexId = indexId;
        this.lookupFailed = false;
        this.lookupCancelled = false;
        this.totalKeysIndexed = -1;
        this.lookupResultSize = -1;

        this.parentTrace = parentTrace;
        if (this.parentTrace == null) {
          depth = 0;
        }
        else {
          depth = this.parentTrace.depth + 1;
        }
        lookupStartedAtMs = System.currentTimeMillis();
      }


      public final void lookupFinished() {
        if (!mustBeStarted()) {
          //if trace wasn't started -> nothing (meaningful) to report
          return;
        }

        try {
          requireNonNull(indexId, "indexId must be set here");
          final long lookupFinishedAtMs = System.currentTimeMillis();
          final long lookupDurationMs = lookupFinishedAtMs - lookupStartedAtMs;

          if (REPORT_INDIVIDUAL_LOOKUPS_TO_FUS) {
            if (lookupDurationMs > REPORT_TO_FUS_INDIVIDUAL_LOOKUPS_ONLY_LONGER_THAN_MS || lookupFailed) {
              reportDetailedDataToFUS(lookupFinishedAtMs);
            }
          }
          if (REPORT_INDIVIDUAL_LOOKUPS_TO_OPEN_TELEMETRY) {
            reportDetailedDataToOTel(lookupFinishedAtMs);
          }

          if (COLLECT_AGGREGATED_STATS) {
            //TODO RC: lookups with N>1 keys should be counted as N lookups during the aggregation?
            collectAggregatedData(lookupFinishedAtMs);
          }
        }
        finally {
          //indexId = null; //intentionally not clear it to provide a bit more debugging info

          if (parentTrace != null) {
            currentTraceHolder.set(parentTrace);
          }
          else {
            depth = -1;//since we re-use root trace object, we need to put it back to un-initialized state
            //or how we will know it is ready-to-use next time?
          }
        }
      }

      protected abstract void reportDetailedDataToFUS(long lookupFinishedAtMs);

      protected abstract void reportDetailedDataToOTel(long lookupFinishedAtMs);

      protected abstract void collectAggregatedData(long lookupFinishedAtMs);

      @Override
      public final void close() {
        lookupFinished();
      }


      /* === Additional info about what was lookup-ed, and context/environment: ================================================ */


      //FIXME: remove this method -- we don't use the project info
      //       or store project.locationHash instead of project ref?
      public T withProject(final @Nullable Project project) {
        return typeSafeThis();
      }

      public T lookupFailed() {
        if (traceWasStarted()) {
          this.lookupFailed = true;
        }
        return typeSafeThis();
      }

      public T lookupCancelled() {
        if (traceWasStarted()) {
          this.lookupCancelled = true;
        }
        return typeSafeThis();
      }

      public T totalKeysIndexed(final int totalKeysIndexed) {
        if (traceWasStarted()) {
          this.totalKeysIndexed = totalKeysIndexed;
        }
        return typeSafeThis();
      }

      public T lookupResultSize(final int size) {
        if (traceWasStarted()) {
          this.lookupResultSize = size;
        }
        return typeSafeThis();
      }

      @Override
      public String toString() {
        return getClass().getSimpleName() +
               "{indexId=" + indexId +
               ", depth=" + depth +
               ", is started? =" + traceWasStarted() +
               ", lookupStartedAtMs=" + lookupStartedAtMs +
               '}';
      }

      /* ======= infrastructure: ================================================================================================ */

      @Override
      @SuppressWarnings("unchecked")
      protected T clone() {
        try {
          return (T)super.clone();
        }
        catch (CloneNotSupportedException e) {
          throw new AssertionError("Code bug: Cloneable must not throw CloneNotSupportedException", e);
        }
      }

      @SuppressWarnings("unchecked")
      private @NotNull T typeSafeThis() {
        return (T)this;
      }

      private void logOrThrowMisuse() {
        final String errorMessage =
          ".lookupStarted() was called " + depth + " times (>" + MAX_LOOKUP_DEPTH + " max) without matching " +
          ".close()/.lookupFinished() -> probably code bug?\n" + this;
        if (THROW_ON_INCORRECT_USAGE) {
          throw new AssertionError(errorMessage);
        }
        else {
          THROTTLED_LOG.warn(errorMessage);
        }
      }

      protected boolean mustBeStarted() {
        final boolean wasStarted = traceWasStarted();
        if (!wasStarted) {
          final String errorMessage = "Code bug: .lookupStarted() must be called before. " + this;
          if (THROW_ON_INCORRECT_USAGE) {
            throw new AssertionError(errorMessage);
          }
          else {
            THROTTLED_LOG.warn(errorMessage);
          }
        }

        return wasStarted;
      }

      protected boolean traceWasStarted() {
        return depth >= 0;
      }
    }


    //========================== 'All keys' lookup reporting:

    public static final ThreadLocal<LookupAllKeysTrace> TRACE_OF_ALL_KEYS_LOOKUP = new ThreadLocal<>() {
      @Override
      protected LookupAllKeysTrace initialValue() {
        return new LookupAllKeysTrace(this);
      }
    };

    /**
     * Holds a trace (timestamps, pieces of data) for a 'lookup entries' index query. To be used as thread-local
     * object.
     */
    public static final class LookupAllKeysTrace extends LookupTraceBase<LookupAllKeysTrace> {
      private long indexValidationFinishedAtMs;

      private LookupAllKeysTrace(final ThreadLocal<LookupAllKeysTrace> current) {
        super(current);
      }

      @Override
      public LookupAllKeysTrace lookupStarted(final @NotNull IndexId<?, ?> indexId) {
        final LookupAllKeysTrace trace = super.lookupStarted(indexId);
        this.indexValidationFinishedAtMs = -1;

        //for re-entrant calls could be != this
        return trace;
      }

      public LookupAllKeysTrace indexValidationFinished() {
        if (traceWasStarted()) {
          indexValidationFinishedAtMs = System.currentTimeMillis();
        }
        return this;
      }

      @Override
      protected void reportDetailedDataToFUS(final long lookupFinishedAtMs) {
        final long lookupDurationMs = lookupFinishedAtMs - lookupStartedAtMs;
        EVENT_INDEX_ALL_KEYS_LOOKUP.log(
          FIELD_INDEX_ID.with(indexId.getName()),

          //indexValidationFinishedAtMs==lookupStartedAtMs if not set due to exception
          // => UP_TO_DATE_CHECK_DURATION_MS would be 0 in that case
          FIELD_UP_TO_DATE_CHECK_DURATION_MS.with(indexValidationFinishedAtMs - lookupStartedAtMs),

          FIELD_LOOKUP_DURATION_MS.with(lookupDurationMs),

          FIELD_LOOKUP_CANCELLED.with(lookupCancelled),
          FIELD_LOOKUP_FAILED.with(lookupFailed),

          FIELD_TOTAL_KEYS_INDEXED_COUNT.with(totalKeysIndexed)
        );
      }

      @Override
      protected void reportDetailedDataToOTel(long lookupFinishedAtMs) {
        Span lookupSpan = OTEL_TRACER.spanBuilder(SPAN_NAME_INDEX_LOOKUP_ALL_KEYS)
          .setAttribute("index_id", indexId.getName())
          .setAttribute("total_keys_in_index", totalKeysIndexed)
          .setAttribute("lookup_result_size", lookupResultSize)
          .setStartTimestamp(lookupStartedAtMs, MILLISECONDS)
          .startSpan();
        lookupSpan.setStatus(lookupFailed ? StatusCode.ERROR : StatusCode.OK);
        //TODO RC: supply .lookupCancelled
        try (Scope scope = lookupSpan.makeCurrent()) {
          OTEL_TRACER.spanBuilder(SPAN_NAME_INDEX_UP_TO_DATE_CHECK)
            .setStartTimestamp(lookupStartedAtMs, MILLISECONDS)
            .startSpan()
            .end(indexValidationFinishedAtMs, MILLISECONDS);
        }

        lookupSpan.end(lookupFinishedAtMs, MILLISECONDS);
      }

      @Override
      protected void collectAggregatedData(final long lookupFinishedAtMs) {
        final long lookupDurationMs = lookupFinishedAtMs - lookupStartedAtMs;
        IndexOperationAggregatesCollector.recordAllKeysLookup(indexId, lookupFailed, lookupDurationMs);
      }
    }

    public static LookupAllKeysTrace lookupAllKeysStarted(final IndexId<?, ?> indexId) {
      return TRACE_OF_ALL_KEYS_LOOKUP.get().lookupStarted(indexId);
    }


    //========================== Entries lookup reporting:

    public static final ThreadLocal<LookupEntriesByKeysTrace> TRACE_OF_ENTRIES_LOOKUP = new ThreadLocal<>() {
      @Override
      protected LookupEntriesByKeysTrace initialValue() {
        return new LookupEntriesByKeysTrace(this);
      }
    };

    /**
     * Holds a trace (timestamps, pieces of data) for a 'lookup entries' index query. To be used as thread-local
     * object.
     */
    public static final class LookupEntriesByKeysTrace extends LookupTraceBase<LookupEntriesByKeysTrace> {
      private long indexValidationFinishedAtMs;

      /**
       * How many keys were looked up (-1 => 'unknown')
       */
      private int lookupKeysCount = -1;
      private LookupOperation lookupOperation = LookupOperation.UNKNOWN;

      private LookupEntriesByKeysTrace(final ThreadLocal<LookupEntriesByKeysTrace> current) {
        super(current);
      }

      @Override
      public LookupEntriesByKeysTrace lookupStarted(final @NotNull IndexId<?, ?> indexId) {
        final LookupEntriesByKeysTrace trace = super.lookupStarted(indexId);

        this.lookupOperation = LookupOperation.UNKNOWN;
        this.lookupKeysCount = -1;
        this.indexValidationFinishedAtMs = -1;

        //for re-entrant calls could be != this
        return trace;
      }

      public LookupEntriesByKeysTrace indexValidationFinished() {
        if (traceWasStarted()) {
          indexValidationFinishedAtMs = System.currentTimeMillis();
        }
        return this;
      }

      @Override
      protected void reportDetailedDataToFUS(final long lookupFinishedAtMs) {
        EVENT_INDEX_LOOKUP_ENTRIES_BY_KEYS.log(
          FIELD_INDEX_ID.with(indexId.getName()),

          FIELD_UP_TO_DATE_CHECK_DURATION_MS.with(
            indexValidationFinishedAtMs > 0 ? indexValidationFinishedAtMs - lookupStartedAtMs : 0),

          FIELD_LOOKUP_DURATION_MS.with(lookupFinishedAtMs - lookupStartedAtMs),

          FIELD_LOOKUP_CANCELLED.with(lookupCancelled),
          FIELD_LOOKUP_FAILED.with(lookupFailed),

          FIELD_LOOKUP_KEYS_OP.with(lookupOperation),
          FIELD_LOOKUP_KEYS_COUNT.with(lookupKeysCount),

          FIELD_TOTAL_KEYS_INDEXED_COUNT.with(totalKeysIndexed),
          FIELD_LOOKUP_RESULT_ENTRIES_COUNT.with(lookupResultSize)
        );
      }

      @Override
      protected void reportDetailedDataToOTel(long lookupFinishedAtMs) {
        Span lookupSpan = OTEL_TRACER.spanBuilder(SPAN_NAME_INDEX_LOOKUP_ENTRIES)
          .setAttribute("index_id", indexId.getName())
          .setAttribute("total_keys_in_index", totalKeysIndexed)
          .setAttribute("lookup_result_size", lookupResultSize)
          .setAttribute("lookup_keys", lookupKeysCount)
          .setAttribute("lookup_op", lookupOperation.name())
          .setStartTimestamp(lookupStartedAtMs, MILLISECONDS)
          .startSpan();
        lookupSpan.setStatus(lookupFailed ? StatusCode.ERROR : StatusCode.OK);
        //TODO RC: supply .lookupCancelled
        try (Scope scope = lookupSpan.makeCurrent()) {
          if (indexValidationFinishedAtMs > 0) {
            OTEL_TRACER.spanBuilder(SPAN_NAME_INDEX_UP_TO_DATE_CHECK)
              .setStartTimestamp(lookupStartedAtMs, MILLISECONDS)
              .startSpan()
              .end(indexValidationFinishedAtMs, MILLISECONDS);
          }
        }

        lookupSpan.end(lookupFinishedAtMs, MILLISECONDS);
      }

      @Override
      protected void collectAggregatedData(final long lookupFinishedAtMs) {
        final long lookupDurationMs = lookupFinishedAtMs - lookupStartedAtMs;
        IndexOperationAggregatesCollector.recordEntriesByKeysLookup(indexId, lookupFailed, lookupDurationMs);
      }

      //=== Additional info about what was lookup-ed, and context/environment:

      public LookupEntriesByKeysTrace keysWithAND(final int keysCount) {
        this.lookupKeysCount = keysCount;
        this.lookupOperation = LookupOperation.AND;
        return this;
      }

      public LookupEntriesByKeysTrace keysWithOR(final int keysCount) {
        this.lookupKeysCount = keysCount;
        this.lookupOperation = LookupOperation.OR;
        return this;
      }
    }

    public static LookupEntriesByKeysTrace lookupEntriesStarted(final IndexId<?, ?> indexId) {
      return TRACE_OF_ENTRIES_LOOKUP.get().lookupStarted(indexId);
    }

    //========================== Stub-Index Entries lookup reporting:

    public static final ThreadLocal<LookupStubEntriesByKeyTrace> TRACE_OF_STUB_ENTRIES_LOOKUP = new ThreadLocal<>() {
      @Override
      protected LookupStubEntriesByKeyTrace initialValue() {
        return new LookupStubEntriesByKeyTrace(this);
      }
    };

    /**
     * Holds a trace (timestamps, pieces of data) for a 'lookup entries' stub-index query. To be used as thread-local
     * object.
     */
    public static final class LookupStubEntriesByKeyTrace extends LookupTraceBase<LookupStubEntriesByKeyTrace> {
      //total lookup time = (upToDateCheck time) + (pure index lookup time) + (Stub Trees deserializing time)
      private long indexValidationFinishedAtMs;
      private long stubTreesDeserializingStarted;

      private LookupStubEntriesByKeyTrace(final ThreadLocal<LookupStubEntriesByKeyTrace> current) {
        super(current);
      }

      @Override
      public LookupStubEntriesByKeyTrace lookupStarted(final @NotNull IndexId<?, ?> indexId) {
        final LookupStubEntriesByKeyTrace trace = super.lookupStarted(indexId);

        indexValidationFinishedAtMs = -1;
        stubTreesDeserializingStarted = -1;

        //for re-entrant calls could be != this;
        return trace;
      }

      public LookupStubEntriesByKeyTrace indexValidationFinished() {
        if (traceWasStarted()) {
          indexValidationFinishedAtMs = System.currentTimeMillis();
        }
        return this;
      }

      public LookupStubEntriesByKeyTrace stubTreesDeserializingStarted() {
        if (traceWasStarted()) {
          stubTreesDeserializingStarted = System.currentTimeMillis();
        }
        return this;
      }

      @Override
      protected void reportDetailedDataToFUS(long lookupFinishedAtMs) {
        EVENT_STUB_INDEX_LOOKUP_ENTRIES_BY_KEY.log(
          FIELD_INDEX_ID.with(indexId.getName()),

          FIELD_UP_TO_DATE_CHECK_DURATION_MS.with(
            indexValidationFinishedAtMs > 0 ? indexValidationFinishedAtMs - lookupStartedAtMs : 0),

          FIELD_STUB_TREE_DESERIALIZING_DURATION_MS.with(
            stubTreesDeserializingStarted > 0 ? lookupFinishedAtMs - stubTreesDeserializingStarted : 0),

          FIELD_LOOKUP_DURATION_MS.with(lookupFinishedAtMs - lookupStartedAtMs),

          FIELD_LOOKUP_CANCELLED.with(lookupCancelled),
          FIELD_LOOKUP_FAILED.with(lookupFailed),

          FIELD_TOTAL_KEYS_INDEXED_COUNT.with(totalKeysIndexed),
          FIELD_LOOKUP_RESULT_ENTRIES_COUNT.with(lookupResultSize)
        );
      }

      @Override
      protected void reportDetailedDataToOTel(long lookupFinishedAtMs) {
        Span lookupSpan = OTEL_TRACER.spanBuilder(SPAN_NAME_INDEX_LOOKUP_STUBS)
          .setAttribute("index_id", indexId.getName())
          .setAttribute("total_keys_in_index", totalKeysIndexed)
          .setAttribute("lookup_result_size", lookupResultSize)
          .setStartTimestamp(lookupStartedAtMs, MILLISECONDS)
          .startSpan();
        lookupSpan.setStatus(lookupFailed ? StatusCode.ERROR : StatusCode.OK);
        //TODO RC: supply .lookupCancelled
        try (Scope scope = lookupSpan.makeCurrent()) {
          if (indexValidationFinishedAtMs > 0) {
            OTEL_TRACER.spanBuilder(SPAN_NAME_INDEX_UP_TO_DATE_CHECK)
              .setStartTimestamp(lookupStartedAtMs, MILLISECONDS)
              .startSpan()
              .end(indexValidationFinishedAtMs, MILLISECONDS);
          }

          if (stubTreesDeserializingStarted > 0) {
            OTEL_TRACER.spanBuilder(SPAN_NAME_STUB_TREE_DESERIALIZATION)
              .setStartTimestamp(stubTreesDeserializingStarted, MILLISECONDS)
              .startSpan()
              .end(lookupFinishedAtMs, MILLISECONDS);
          }
        }

        lookupSpan.end(lookupFinishedAtMs, MILLISECONDS);
      }

      @Override
      protected void collectAggregatedData(long lookupFinishedAtMs) {
        final long lookupDurationMs = lookupFinishedAtMs - lookupStartedAtMs;
        IndexOperationAggregatesCollector.recordStubEntriesByKeysLookup(indexId, lookupFailed, lookupDurationMs);
      }
    }

    public static LookupStubEntriesByKeyTrace lookupStubEntriesStarted(final IndexId<?, ?> indexId) {
      return TRACE_OF_STUB_ENTRIES_LOOKUP.get().lookupStarted(indexId);
    }
  }

  /** Collects and reports aggregated performance data (averages, %-iles) about index lookup timings to FUS */
  public static final class IndexOperationAggregatesCollector extends ApplicationUsagesCollector {

    public static final int MAX_TRACKABLE_DURATION_MS = getIntProperty("IndexLookupTimingsReporting.MAX_TRACKABLE_DURATION_MS", 5000);

    private static final IntEventField FIELD_LOOKUPS_TOTAL = EventFields.Int("lookups_total");
    private static final IntEventField FIELD_LOOKUPS_FAILED = EventFields.Int("lookups_failed");
    private static final IntEventField FIELD_LOOKUPS_CANCELLED = EventFields.Int("lookups_cancelled");
    private static final DoubleEventField FIELD_LOOKUP_DURATION_MEAN = EventFields.Double("lookup_duration_mean_ms");
    private static final IntEventField FIELD_LOOKUP_DURATION_90P = EventFields.Int("lookup_duration_90ile_ms");
    private static final IntEventField FIELD_LOOKUP_DURATION_95P = EventFields.Int("lookup_duration_95ile_ms");
    private static final IntEventField FIELD_LOOKUP_DURATION_99P = EventFields.Int("lookup_duration_99ile_ms");
    private static final IntEventField FIELD_LOOKUP_DURATION_MAX = EventFields.Int("lookup_duration_max_ms");


    private static final VarargEventId EVENT_INDEX_ALL_KEYS_LOOKUP = INDEX_USAGE_AGGREGATES_GROUP.registerVarargEvent(
      "lookup.all_keys",
      FIELD_INDEX_ID,

      FIELD_LOOKUPS_TOTAL,
      FIELD_LOOKUPS_FAILED,
      FIELD_LOOKUPS_CANCELLED,

      FIELD_LOOKUP_DURATION_MEAN,
      FIELD_LOOKUP_DURATION_90P,
      FIELD_LOOKUP_DURATION_95P,
      FIELD_LOOKUP_DURATION_99P,
      FIELD_LOOKUP_DURATION_MAX
    );

    private static final VarargEventId EVENT_INDEX_ENTRIES_LOOKUP = INDEX_USAGE_AGGREGATES_GROUP.registerVarargEvent(
      "lookup.entries",
      FIELD_INDEX_ID,

      FIELD_LOOKUPS_TOTAL,
      FIELD_LOOKUPS_FAILED,
      FIELD_LOOKUPS_CANCELLED,

      FIELD_LOOKUP_DURATION_MEAN,
      FIELD_LOOKUP_DURATION_90P,
      FIELD_LOOKUP_DURATION_95P,
      FIELD_LOOKUP_DURATION_99P,
      FIELD_LOOKUP_DURATION_MAX
    );

    private static final VarargEventId EVENT_STUB_INDEX_ENTRIES_LOOKUP = INDEX_USAGE_AGGREGATES_GROUP.registerVarargEvent(
      "lookup.stub_entries",
      FIELD_INDEX_ID,

      FIELD_LOOKUPS_TOTAL,
      FIELD_LOOKUPS_FAILED,
      FIELD_LOOKUPS_CANCELLED,

      FIELD_LOOKUP_DURATION_MEAN,
      FIELD_LOOKUP_DURATION_90P,
      FIELD_LOOKUP_DURATION_95P,
      FIELD_LOOKUP_DURATION_99P,
      FIELD_LOOKUP_DURATION_MAX
    );

    private static final ConcurrentHashMap<IndexId<?, ?>, Recorder> allKeysLookupDurationsMsByIndexId = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<IndexId<?, ?>, Recorder> entriesLookupDurationsMsByIndexId = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<IndexId<?, ?>, Recorder> stubEntriesLookupDurationsMsByIndexId = new ConcurrentHashMap<>();

    //FIXME RC: OTel reporting is implicitly guarded by REPORT_AGGREGATED_STATS, which is not obvious. Better to separate FUS and
    //          OTel reporting to independent branches
    private static final @Nullable IndexLookupTimingsReporting.IndexOperationToOTelMetricsReporter otelReporter =
      REPORT_AGGREGATED_STATS_TO_OPEN_TELEMETRY ?
      new IndexOperationToOTelMetricsReporter() :
      null;


    public IndexOperationAggregatesCollector() {
      if (!isValid()) {
        throw new AssertionError(getGroup() + " is not valid groupId");
      }
    }

    @Override
    public EventLogGroup getGroup() {
      return INDEX_USAGE_AGGREGATES_GROUP;
    }

    public static void recordAllKeysLookup(final IndexId<?, ?> indexId,
                                           final boolean failed,
                                           final long durationMs) {
      if (!failed) {
        final Recorder recorder = allKeysLookupDurationsMsByIndexId.computeIfAbsent(
          indexId,
          __ -> new Recorder(
            MAX_TRACKABLE_DURATION_MS,
            /* significant digits = */ 2     /* ~1% accuracy */
          )
        );

        final long clampedDurationMs = MathUtil.clamp(durationMs, 0, MAX_TRACKABLE_DURATION_MS);
        //Recorder allows concurrent writes:
        recorder.recordValue(clampedDurationMs);
        if (otelReporter != null) {
          otelReporter.reportAllKeysLookup(clampedDurationMs);
        }
      }
    }

    public static void recordEntriesByKeysLookup(final IndexId<?, ?> indexId,
                                                 final boolean failed,
                                                 final long durationMs) {
      if (!failed) {
        final Recorder recorder = entriesLookupDurationsMsByIndexId.computeIfAbsent(
          indexId,
          __ -> new Recorder(
            MAX_TRACKABLE_DURATION_MS,
            /* significant digits = */ 2     /* ~1% accuracy */
          )
        );

        final long clampedDurationMs = MathUtil.clamp(durationMs, 0, MAX_TRACKABLE_DURATION_MS);
        //Recorder allows concurrent writes:
        recorder.recordValue(clampedDurationMs);
        if (otelReporter != null) {
          otelReporter.reportEntryLookup(clampedDurationMs);
        }
      }
    }

    public static void recordStubEntriesByKeysLookup(final IndexId<?, ?> indexId,
                                                     final boolean failed,
                                                     final long durationMs) {
      if (!failed) {
        final Recorder recorder = stubEntriesLookupDurationsMsByIndexId.computeIfAbsent(
          indexId,
          __ -> new Recorder(
            MAX_TRACKABLE_DURATION_MS,
            /* significant digits = */ 2     /* ~1% accuracy */
          )
        );

        final long clampedDurationMs = MathUtil.clamp(durationMs, 0, MAX_TRACKABLE_DURATION_MS);
        //Recorder allows concurrent writes:
        recorder.recordValue(clampedDurationMs);
        if (otelReporter != null) {
          otelReporter.recordStubEntryLookup(clampedDurationMs);
        }
      }
    }

    @Override
    public @NotNull Set<MetricEvent> getMetrics() {
      if (REPORT_AGGREGATED_STATS_TO_FUS) {
        final Set<MetricEvent> allKeysLookupStats = allKeysLookupDurationsMsByIndexId.entrySet().stream().map(e -> {
          final IndexId<?, ?> indexId = e.getKey();
          final Recorder recorderForIndex = e.getValue();
          final Histogram recordedValuesHistogram = recorderForIndex.getIntervalHistogram();
          return EVENT_INDEX_ALL_KEYS_LOOKUP.metric(
            FIELD_INDEX_ID.with(indexId.getName()),

            FIELD_LOOKUPS_TOTAL.with((int)recordedValuesHistogram.getTotalCount()),
            //TODO FIELD_LOOKUPS_FAILED.with( -1 ),
            //TODO FIELD_LOOKUPS_CANCELLED.with( -1 ),

            FIELD_LOOKUP_DURATION_MEAN.with(recordedValuesHistogram.getMean()),

            FIELD_LOOKUP_DURATION_90P.with((int)recordedValuesHistogram.getValueAtPercentile(90)),
            FIELD_LOOKUP_DURATION_95P.with((int)recordedValuesHistogram.getValueAtPercentile(95)),
            FIELD_LOOKUP_DURATION_99P.with((int)recordedValuesHistogram.getValueAtPercentile(99)),
            FIELD_LOOKUP_DURATION_MAX.with((int)recordedValuesHistogram.getMaxValue()
            )
          );
        }).collect(toSet());
        final Set<MetricEvent> entriesLookupStats = entriesLookupDurationsMsByIndexId.entrySet().stream().map(e -> {
          final IndexId<?, ?> indexId = e.getKey();
          final Recorder recorderForIndex = e.getValue();
          final Histogram recordedValuesHistogram = recorderForIndex.getIntervalHistogram();
          return EVENT_INDEX_ENTRIES_LOOKUP.metric(
            FIELD_INDEX_ID.with(indexId.getName()),

            FIELD_LOOKUPS_TOTAL.with((int)recordedValuesHistogram.getTotalCount()),
            //TODO FIELD_LOOKUPS_FAILED.with( -1 ),
            //TODO FIELD_LOOKUPS_CANCELLED.with( -1 ),

            FIELD_LOOKUP_DURATION_MEAN.with(recordedValuesHistogram.getMean()),

            FIELD_LOOKUP_DURATION_90P.with((int)recordedValuesHistogram.getValueAtPercentile(90)),
            FIELD_LOOKUP_DURATION_95P.with((int)recordedValuesHistogram.getValueAtPercentile(95)),
            FIELD_LOOKUP_DURATION_99P.with((int)recordedValuesHistogram.getValueAtPercentile(99)),
            FIELD_LOOKUP_DURATION_MAX.with((int)recordedValuesHistogram.getMaxValue()
            )
          );
        }).collect(toSet());
        final Set<MetricEvent> stubEntriesLookupStats = stubEntriesLookupDurationsMsByIndexId.entrySet().stream().map(e -> {
          final IndexId<?, ?> indexId = e.getKey();
          final Recorder recorderForIndex = e.getValue();
          final Histogram recordedValuesHistogram = recorderForIndex.getIntervalHistogram();
          return EVENT_STUB_INDEX_ENTRIES_LOOKUP.metric(
            FIELD_INDEX_ID.with(indexId.getName()),

            FIELD_LOOKUPS_TOTAL.with((int)recordedValuesHistogram.getTotalCount()),
            //TODO FIELD_LOOKUPS_FAILED.with( -1 ),
            //TODO FIELD_LOOKUPS_CANCELLED.with( -1 ),

            FIELD_LOOKUP_DURATION_MEAN.with(recordedValuesHistogram.getMean()),

            FIELD_LOOKUP_DURATION_90P.with((int)recordedValuesHistogram.getValueAtPercentile(90)),
            FIELD_LOOKUP_DURATION_95P.with((int)recordedValuesHistogram.getValueAtPercentile(95)),
            FIELD_LOOKUP_DURATION_99P.with((int)recordedValuesHistogram.getValueAtPercentile(99)),
            FIELD_LOOKUP_DURATION_MAX.with((int)recordedValuesHistogram.getMaxValue()
            )
          );
        }).collect(toSet());

        final HashSet<MetricEvent> all = new HashSet<>();
        all.addAll(allKeysLookupStats);
        all.addAll(entriesLookupStats);
        all.addAll(stubEntriesLookupStats);
        return all;
      }
      else {
        return Set.of(
          EVENT_INDEX_ALL_KEYS_LOOKUP.metric(FIELD_INDEX_ID.with(""))
        );
      }
    }
  }

  /** Collects and reports aggregated performance data (averages, %-iles) about index lookup timings to OpenTelemetry */
  private static final class IndexOperationToOTelMetricsReporter implements AutoCloseable {

    // 2 digits =~ 1% accuracy
    private static final Recorder allKeysLookupDurationMsHisto = new Recorder(MAX_TRACKABLE_DURATION_MS, /* significant digits = */ 2);
    private static final Recorder entriesLookupDurationsMsHisto = new Recorder(MAX_TRACKABLE_DURATION_MS, /* significant digits = */ 2);
    private static final Recorder stubEntriesLookupDurationsMsHisto = new Recorder(MAX_TRACKABLE_DURATION_MS, /* significant digits = */ 2);


    private final ObservableLongMeasurement allKeysTotalLookups;
    private final ObservableDoubleMeasurement allKeysLookupDurationAvg;
    private final ObservableDoubleMeasurement allKeysLookupDuration90P;
    private final ObservableDoubleMeasurement allKeysLookupDurationMax;

    private final ObservableLongMeasurement entriesTotalLookups;
    private final ObservableDoubleMeasurement entriesLookupDurationAvg;
    private final ObservableDoubleMeasurement entriesLookupDuration90P;
    private final ObservableDoubleMeasurement entriesLookupDurationMax;

    private final ObservableLongMeasurement stubsTotalLookups;
    private final ObservableDoubleMeasurement stubsLookupDurationAvg;
    private final ObservableDoubleMeasurement stubsLookupDuration90P;
    private final ObservableDoubleMeasurement stubsLookupDurationMax;

    /** Needed only to stop reporting on .close() */
    private final BatchCallback batchCallbackHandle;

    private IndexOperationToOTelMetricsReporter() {
      final Meter meter = TelemetryManager.getInstance().getMeter(Indexes);

      //RC: It is important to use 'gauge', NOT 'counter' for the metrics below. This is because 'counters' requires
      //    reporting cumulative total since the _session start_, while we report values accumulated only _between_
      //    the reporting points, not accumulated since JVM start -- i.e. we reset most of the accumulators to 0
      //    in .drainValuesToOTel().
      //    The only exception is xxxLookups counters -- we don't reset them, keep the value accumulating since session
      //    start -- i.e. it is the natural Counter (AT-534)

      allKeysTotalLookups = meter.counterBuilder("Indexes.allKeys.lookups").buildObserver();
      allKeysLookupDurationAvg = meter.gaugeBuilder("Indexes.allKeys.lookupDurationAvgMs").buildObserver();
      allKeysLookupDuration90P = meter.gaugeBuilder("Indexes.allKeys.lookupDuration90PMs").buildObserver();
      allKeysLookupDurationMax = meter.gaugeBuilder("Indexes.allKeys.lookupDurationMaxMs").buildObserver();

      entriesTotalLookups = meter.counterBuilder("Indexes.entries.lookups").buildObserver();
      entriesLookupDurationAvg = meter.gaugeBuilder("Indexes.entries.lookupDurationAvgMs").buildObserver();
      entriesLookupDuration90P = meter.gaugeBuilder("Indexes.entries.lookupDuration90PMs").buildObserver();
      entriesLookupDurationMax = meter.gaugeBuilder("Indexes.entries.lookupDurationMaxMs").buildObserver();

      stubsTotalLookups = meter.counterBuilder("Indexes.stubs.lookups").buildObserver();
      stubsLookupDurationAvg = meter.gaugeBuilder("Indexes.stubs.lookupDurationAvgMs").buildObserver();
      stubsLookupDuration90P = meter.gaugeBuilder("Indexes.stubs.lookupDuration90PMs").buildObserver();
      stubsLookupDurationMax = meter.gaugeBuilder("Indexes.stubs.lookupDurationMaxMs").buildObserver();

      batchCallbackHandle = meter.batchCallback(
        this::drainValuesToOTel,
        allKeysTotalLookups, allKeysLookupDurationAvg, allKeysLookupDuration90P, allKeysLookupDurationMax,
        entriesTotalLookups, entriesLookupDurationAvg, entriesLookupDuration90P, entriesLookupDurationMax,
        stubsTotalLookups, stubsLookupDurationAvg, stubsLookupDuration90P, stubsLookupDurationMax
      );
    }

    //cached interval histograms:
    private transient Histogram allKeysIntervalHisto;
    private long allKeysLookups = 0;
    private transient Histogram entriesIntervalHisto;
    private long entriesLookups = 0;
    private transient Histogram stubsIntervalHisto;
    private long stubsLookups = 0;


    private void drainValuesToOTel() {
      allKeysIntervalHisto = allKeysLookupDurationMsHisto.getIntervalHistogram(allKeysIntervalHisto);
      allKeysTotalLookups.record(allKeysLookups);
      allKeysLookupDurationAvg.record(allKeysIntervalHisto.getMean());
      allKeysLookupDuration90P.record(allKeysIntervalHisto.getValueAtPercentile(90));
      allKeysLookupDurationMax.record(allKeysIntervalHisto.getMaxValue());

      entriesIntervalHisto = entriesLookupDurationsMsHisto.getIntervalHistogram(entriesIntervalHisto);
      entriesTotalLookups.record(entriesLookups);
      entriesLookupDurationAvg.record(entriesIntervalHisto.getMean());
      entriesLookupDuration90P.record(entriesIntervalHisto.getValueAtPercentile(90));
      entriesLookupDurationMax.record(entriesIntervalHisto.getMaxValue());

      stubsIntervalHisto = stubEntriesLookupDurationsMsHisto.getIntervalHistogram(stubsIntervalHisto);
      stubsTotalLookups.record(stubsLookups);
      stubsLookupDurationAvg.record(stubsIntervalHisto.getMean());
      stubsLookupDuration90P.record(stubsIntervalHisto.getValueAtPercentile(90));
      stubsLookupDurationMax.record(stubsIntervalHisto.getMaxValue());
    }

    public void reportAllKeysLookup(final long clampedDurationMs) {
      allKeysLookupDurationMsHisto.recordValue(clampedDurationMs);
      allKeysLookups++;
    }

    public void reportEntryLookup(final long clampedDurationMs) {
      entriesLookupDurationsMsHisto.recordValue(clampedDurationMs);
      entriesLookups++;
    }

    public void recordStubEntryLookup(final long clampedDurationMs) {
      stubEntriesLookupDurationsMsHisto.recordValue(clampedDurationMs);
      stubsLookups++;
    }

    @Override
    public void close() {
      batchCallbackHandle.close();
    }
  }
}
