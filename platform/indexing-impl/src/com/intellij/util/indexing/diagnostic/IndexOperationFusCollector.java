// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Locale;

import static java.util.Objects.requireNonNull;

/**
 * Collects and reports performance data (timings mostly) about index usage (lookup). Right now there 'all keys'
 * lookups and 'value(s) by key(s)' lookups are timed and reported with some additional infos.
 * <p>
 * There are 3 type of lookups measured now:
 * <ol>
 *   <li>lookup all keys in index ({@link #INDEX_ALL_KEYS_LOOKUP_EVENT})</li>
 * <li>lookup entries by key(s) from file-based index ({@link #INDEX_LOOKUP_ENTRIES_BY_KEYS_EVENT})</li>
 * <li>lookup entries by key from stub-index ({@link #STUB_INDEX_LOOKUP_ENTRIES_BY_KEY_EVENT})</li>
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
public class IndexOperationFusCollector extends CounterUsagesCollector {
  private static final Logger LOG = Logger.getInstance(IndexOperationFusCollector.class);

  /**
   * If true -> throw exception if tracing methods are called in incorrect order (e.g. .finish() before start()).
   * if false (default) -> log warning on incorrect sequence of calls, but try to continue normal operation afterwards.
   * <p>
   * Really, value=true is only useful for debugging -- in production reporting are generally not expected to throw exceptions.
   */
  @VisibleForTesting
  static final boolean THROW_ON_INCORRECT_USAGE =
    Boolean.getBoolean("IndexOperationFusStatisticsCollector.THROW_ON_INCORRECT_USAGE");

  /**
   * Report lookup operation X to analytics only if total duration of the operation X {@code >REPORT_ONLY_OPERATIONS_LONGER_THAN_MS}.
   * There are a lot of index lookups, and this threshold allows to reduce reporting traffic, since we're really only interested in
   * long operations. Default value 0 means 'report lookups >0ms only'.
   * <p>
   * BEWARE: different values for that parameter correspond to a different way of sampling, hence, in theory, should be treated as
   * different event schema _version_.
   */
  public static final int REPORT_ONLY_OPERATIONS_LONGER_THAN_MS =
    Integer.getInteger("IndexOperationFusStatisticsCollector.THROW_ON_INCORRECT_USAGE", 10);


  private static final EventLogGroup INDEX_USAGE_GROUP = new EventLogGroup("index.usage", 1);

  // ================== EVENTS FIELDS:

  private static final StringEventField INDEX_ID_FIELD =
    EventFields.StringValidatedByCustomRule("index_id", IndexIdRuleValidator.class);

  private static final BooleanEventField LOOKUP_FAILED_FIELD = EventFields.Boolean("lookup_failed");

  /**
   * Total lookup time, as it is seen by 'client' (i.e. including up-to-date/validation, and stubs deserializing, etc...)
   */
  private static final LongEventField LOOKUP_DURATION_MS_FIELD = EventFields.Long("lookup_duration_ms");
  private static final LongEventField UP_TO_DATE_CHECK_DURATION_MS_FIELD = EventFields.Long("up_to_date_check_ms");
  private static final LongEventField STUB_TREE_DESERIALIZING_DURATION_MS_FIELD = EventFields.Long("psi_tree_deserializing_ms");

  /**
   * How many keys were lookup-ed (there are methods to lookup >1 keys at once)
   */
  private static final IntEventField LOOKUP_KEYS_COUNT_FIELD = EventFields.Int("keys");
  /**
   * For cases >1 keys lookup: what operation is applied (AND/OR)
   */
  private static final EnumEventField<LookupOperation> LOOKUP_KEYS_OP_FIELD =
    EventFields.Enum("lookup_op", LookupOperation.class, kind -> kind.name().toLowerCase(Locale.US));
  /**
   * How many keys (approximately) current index contains in total -- kind of 'lookup scale'
   */
  private static final IntEventField TOTAL_KEYS_INDEXED_COUNT_FIELD = EventFields.Int("total_keys_indexed");
  private static final IntEventField LOOKUP_RESULT_ENTRIES_COUNT_FIELD = EventFields.Int("entries_found");

  // ================== EVENTS:

  private static final VarargEventId INDEX_ALL_KEYS_LOOKUP_EVENT = INDEX_USAGE_GROUP.registerVarargEvent(
    "lookup.all_keys",
    INDEX_ID_FIELD,

    LOOKUP_FAILED_FIELD,

    LOOKUP_DURATION_MS_FIELD,          //LOOKUP_DURATION = (UP_TO_DATE_CHECK_DURATION) + (pure index lookup time)
    UP_TO_DATE_CHECK_DURATION_MS_FIELD,

    TOTAL_KEYS_INDEXED_COUNT_FIELD
    //LOOKUP_RESULT_ENTRIES_COUNT is useless here, since it == TOTAL_KEYS_INDEXED_COUNT
  );

  private static final VarargEventId INDEX_LOOKUP_ENTRIES_BY_KEYS_EVENT = INDEX_USAGE_GROUP.registerVarargEvent(
    "lookup.entries",
    INDEX_ID_FIELD,

    LOOKUP_FAILED_FIELD,

    LOOKUP_DURATION_MS_FIELD,       //LOOKUP_DURATION = (UP_TO_DATE_CHECK_DURATION) + (pure index lookup time)
    UP_TO_DATE_CHECK_DURATION_MS_FIELD,

    LOOKUP_KEYS_COUNT_FIELD,
    LOOKUP_KEYS_OP_FIELD,
    TOTAL_KEYS_INDEXED_COUNT_FIELD,
    LOOKUP_RESULT_ENTRIES_COUNT_FIELD
  );

  private static final VarargEventId STUB_INDEX_LOOKUP_ENTRIES_BY_KEY_EVENT = INDEX_USAGE_GROUP.registerVarargEvent(
    "lookup.stub_entries",
    INDEX_ID_FIELD,

    LOOKUP_FAILED_FIELD,

    //LOOKUP_DURATION = (UP_TO_DATE_CHECK_DURATION) + (pure index lookup time) + (STUB_TREE_DESERIALIZING_DURATION)
    LOOKUP_DURATION_MS_FIELD,
    UP_TO_DATE_CHECK_DURATION_MS_FIELD,
    STUB_TREE_DESERIALIZING_DURATION_MS_FIELD,

    //RC: StubIndex doesn't have methods to lookup >1 keys at once, so LOOKUP_KEYS_COUNT/LOOKUP_KEYS_OP is useless here
    TOTAL_KEYS_INDEXED_COUNT_FIELD,
    LOOKUP_RESULT_ENTRIES_COUNT_FIELD
  );

  // ================== IMPLEMENTATION METHODS:

  @Override
  public EventLogGroup getGroup() {
    return INDEX_USAGE_GROUP;
  }

  //========================== CLASSES:

  private static abstract class LookupTraceBase<T extends LookupTraceBase<T>> implements AutoCloseable {
    protected boolean traceWasStarted = false;
    protected @Nullable IndexId<?, ?> indexId;
    protected @Nullable Project project;

    protected long lookupStartedAtMs;
    protected boolean lookupFailed;
    protected int totalKeysIndexed;
    protected int lookupResultSize;

    protected T lookupStarted(final @NotNull IndexId<?, ?> indexId) {
      ensureNotYetStarted();
      //if not thrown -> continue as-if no previous trace exists, i.e. overwrite all data remaining from unfinished trace

      this.indexId = indexId;
      this.project = null;
      this.lookupFailed = false;
      this.totalKeysIndexed = -1;
      this.lookupResultSize = -1;

      traceWasStarted = true;
      lookupStartedAtMs = System.currentTimeMillis();
      return typeSafeThis();
    }

    public void lookupFinished() {
      if (!mustBeStarted()) {
        //if trace wasn't started -> nothing (meaningful) to report
        return;
      }

      try {
        requireNonNull(indexId, "indexId must be set here");
        final long finishedAtMs = System.currentTimeMillis();
        final long lookupDurationMs = finishedAtMs - lookupStartedAtMs;
        if (lookupDurationMs > REPORT_ONLY_OPERATIONS_LONGER_THAN_MS || lookupFailed) {
          reportGatheredDataToAnalytics();
        }
      }
      finally {
        traceWasStarted = false;
        indexId = null;
        project = null;//don't hold reference in thread-local
      }
    }

    protected abstract void reportGatheredDataToAnalytics();

    @Override
    public final void close() {
      lookupFinished();
    }

    //=== Additional info about what was lookup-ed, and context/environment:

    public T withProject(final @Nullable Project project) {
      if (traceWasStarted) {
        this.project = project;
      }
      return typeSafeThis();
    }

    public T lookupFailed() {
      if (traceWasStarted) {
        this.lookupFailed = true;
      }
      return typeSafeThis();
    }

    public T totalKeysIndexed(final int totalKeysIndexed) {
      if (traceWasStarted) {
        this.totalKeysIndexed = totalKeysIndexed;
      }
      return typeSafeThis();
    }

    public T lookupResultSize(final int size) {
      if (traceWasStarted) {
        this.lookupResultSize = size;
      }
      return typeSafeThis();
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private T typeSafeThis() {
      return (T)this;
    }

    private void ensureNotYetStarted() {
      if (traceWasStarted) {
        final String errorMessage = "Code bug: .lookupStarted() was already called, not paired with .lookupFinished() yet. " + this;
        if (THROW_ON_INCORRECT_USAGE) {
          throw new AssertionError(errorMessage);
        }
        else {
          LOG.warn(errorMessage);
        }
      }
    }

    protected boolean mustBeStarted() {
      if (!traceWasStarted) {
        final String errorMessage = "Code bug: .lookupStarted() must be called before. " + this;
        if (THROW_ON_INCORRECT_USAGE) {
          throw new AssertionError(errorMessage);
        }
        else {
          LOG.warn(errorMessage);
        }
      }

      return traceWasStarted;
    }

    public String toString() {
      return getClass().getSimpleName() +
             "{indexId=" + indexId +
             ", project=" + project +
             ", is started? =" + traceWasStarted +
             ", lookupStartedAtMs=" + lookupStartedAtMs +
             '}';
    }
  }


  //========================== 'All keys' lookup reporting:

  //FIXME RC: I expected index lookup are not re-entrant, hence single thread-local buffer for params is enough. But
  //          this proves to be false: index lookups are re-entrant, and there are regular scenarios there index lookup
  //          invoked inside another index lookup. Right now this leads to only the deepest lookup be reported, and all
  //          data of lookups above it are dropped, with WARN-ings in logs -- which is not a big issue, but still an issue.
  //          To deal with it correctly thread-local _stack_ of buffers is needed really

  public static final ThreadLocal<LookupAllKeysTrace> TRACE_OF_ALL_KEYS_LOOKUP = ThreadLocal.withInitial(LookupAllKeysTrace::new);

  /**
   * Holds a trace (timestamps, pieces of data) for a 'lookup entries' index query. To be used as thread-local
   * object.
   */
  public static class LookupAllKeysTrace extends LookupTraceBase<LookupAllKeysTrace> {
    private long indexValidationFinishedAtMs;

    @Override
    public LookupAllKeysTrace lookupStarted(final @NotNull IndexId<?, ?> indexId) {
      super.lookupStarted(indexId);
      this.indexValidationFinishedAtMs = -1;

      return this;
    }

    public LookupAllKeysTrace indexValidationFinished() {
      if (traceWasStarted) {
        indexValidationFinishedAtMs = System.currentTimeMillis();
      }
      return this;
    }

    @Override
    protected void reportGatheredDataToAnalytics() {
      final long lookupFinishedAtMs = System.currentTimeMillis();
      INDEX_ALL_KEYS_LOOKUP_EVENT.log(
        project,

        INDEX_ID_FIELD.with(indexId.getName()),

        //indexValidationFinishedAtMs==lookupStartedAtMs if not set due to exception
        // => UP_TO_DATE_CHECK_DURATION_MS would be 0 in that case
        UP_TO_DATE_CHECK_DURATION_MS_FIELD.with(indexValidationFinishedAtMs - lookupStartedAtMs),

        LOOKUP_DURATION_MS_FIELD.with(lookupFinishedAtMs - lookupStartedAtMs),

        LOOKUP_FAILED_FIELD.with(lookupFailed),

        TOTAL_KEYS_INDEXED_COUNT_FIELD.with(totalKeysIndexed)
      );
    }
  }

  public static LookupAllKeysTrace lookupAllKeysStarted(final IndexId<?, ?> indexId) {
    return TRACE_OF_ALL_KEYS_LOOKUP.get().lookupStarted(indexId);
  }


  //========================== Entries lookup reporting:

  public static final ThreadLocal<LookupEntriesByKeysTrace> TRACE_OF_ENTRIES_LOOKUP =
    ThreadLocal.withInitial(LookupEntriesByKeysTrace::new);

  /**
   * Holds a trace (timestamps, pieces of data) for a 'lookup entries' index query. To be used as thread-local
   * object.
   */
  public static class LookupEntriesByKeysTrace extends LookupTraceBase<LookupEntriesByKeysTrace> {
    private long indexValidationFinishedAtMs;

    /**
     * How many keys were looked up (-1 => 'unknown')
     */
    private int lookupKeysCount = -1;
    private LookupOperation lookupOperation = LookupOperation.UNKNOWN;


    @Override
    public LookupEntriesByKeysTrace lookupStarted(final @NotNull IndexId<?, ?> indexId) {
      super.lookupStarted(indexId);

      this.lookupOperation = LookupOperation.UNKNOWN;
      this.lookupKeysCount = -1;
      this.indexValidationFinishedAtMs = -1;

      return this;
    }

    public LookupEntriesByKeysTrace indexValidationFinished() {
      if (traceWasStarted) {
        indexValidationFinishedAtMs = System.currentTimeMillis();
      }
      return this;
    }

    @Override
    protected void reportGatheredDataToAnalytics() {
      final long lookupFinishedAtMs = System.currentTimeMillis();
      INDEX_LOOKUP_ENTRIES_BY_KEYS_EVENT.log(
        project,

        INDEX_ID_FIELD.with(indexId.getName()),

        UP_TO_DATE_CHECK_DURATION_MS_FIELD.with(
          indexValidationFinishedAtMs > 0 ? indexValidationFinishedAtMs - lookupStartedAtMs : 0),

        LOOKUP_DURATION_MS_FIELD.with(lookupFinishedAtMs - lookupStartedAtMs),

        LOOKUP_FAILED_FIELD.with(lookupFailed),

        LOOKUP_KEYS_OP_FIELD.with(lookupOperation),
        LOOKUP_KEYS_COUNT_FIELD.with(lookupKeysCount),

        TOTAL_KEYS_INDEXED_COUNT_FIELD.with(totalKeysIndexed),
        LOOKUP_RESULT_ENTRIES_COUNT_FIELD.with(lookupResultSize)
      );
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

  enum LookupOperation {AND, OR, UNKNOWN}

  //========================== Stub-Index Entries lookup reporting:

  public static final ThreadLocal<LookupStubEntriesByKeyTrace> TRACE_OF_STUB_ENTRIES_LOOKUP = ThreadLocal.withInitial(
    LookupStubEntriesByKeyTrace::new);

  /**
   * Holds a trace (timestamps, pieces of data) for a 'lookup entries' stub-index query. To be used as thread-local
   * object.
   */
  public static class LookupStubEntriesByKeyTrace extends LookupTraceBase<LookupStubEntriesByKeyTrace> {
    //total lookup time = (upToDateCheck time) + (pure index lookup time) + (Stub Trees deserializing time)
    private long indexValidationFinishedAtMs;
    private long stubTreesDeserializingStarted;

    @Override
    public LookupStubEntriesByKeyTrace lookupStarted(final @NotNull IndexId<?, ?> indexId) {
      super.lookupStarted(indexId);

      indexValidationFinishedAtMs = -1;
      stubTreesDeserializingStarted = -1;

      return this;
    }

    public LookupStubEntriesByKeyTrace indexValidationFinished() {
      if (traceWasStarted) {
        indexValidationFinishedAtMs = System.currentTimeMillis();
      }
      return this;
    }

    public LookupStubEntriesByKeyTrace stubTreesDeserializingStarted() {
      if (traceWasStarted) {
        stubTreesDeserializingStarted = System.currentTimeMillis();
      }
      return this;
    }

    @Override
    protected void reportGatheredDataToAnalytics() {
      final long lookupFinishedAtMs = System.currentTimeMillis();
      STUB_INDEX_LOOKUP_ENTRIES_BY_KEY_EVENT.log(
        project,

        INDEX_ID_FIELD.with(indexId.getName()),

        UP_TO_DATE_CHECK_DURATION_MS_FIELD.with(
          indexValidationFinishedAtMs > 0 ? indexValidationFinishedAtMs - lookupStartedAtMs : 0),

        STUB_TREE_DESERIALIZING_DURATION_MS_FIELD.with(
          stubTreesDeserializingStarted > 0 ? lookupFinishedAtMs - stubTreesDeserializingStarted : 0),

        LOOKUP_DURATION_MS_FIELD.with(lookupFinishedAtMs - lookupStartedAtMs),

        LOOKUP_FAILED_FIELD.with(lookupFailed),

        TOTAL_KEYS_INDEXED_COUNT_FIELD.with(totalKeysIndexed),
        LOOKUP_RESULT_ENTRIES_COUNT_FIELD.with(lookupResultSize)
      );
    }
  }

  public static LookupStubEntriesByKeyTrace lookupStubEntriesStarted(final IndexId<?, ?> indexId) {
    return TRACE_OF_STUB_ENTRIES_LOOKUP.get().lookupStarted(indexId);
  }
}
