// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.IndexId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Locale;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

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
public class IndexOperationFusCollector extends CounterUsagesCollector {
  private static final Logger LOG = Logger.getInstance(IndexOperationFusCollector.class);
  private static final ThrottledLogger THROTTLED_LOG = new ThrottledLogger(LOG, SECONDS.toMillis(10));

  /**
   * If true -> throw exception if tracing methods are (likely) called in incorrect order (e.g. .finish() before start(),
   * or .finish() without .start() beforehand).
   * if false (default) -> log warning on incorrect sequence of calls, but try to continue normal operation afterwards.
   * <p>
   */
  @VisibleForTesting
  static final boolean THROW_ON_INCORRECT_USAGE =
    "true".equals(System.getProperty("IndexOperationFusCollector.THROW_ON_INCORRECT_USAGE"));

  /**
   * Report lookup operation X to analytics only if total duration of the operation X {@code >REPORT_ONLY_OPERATIONS_LONGER_THAN_MS}.
   * There are a lot of index lookups, and this threshold allows to reduce reporting traffic, since we're really only interested in
   * long operations. Default value 0 means 'report lookups >0ms only'.
   * <p>
   * BEWARE: different values for that parameter correspond to a different way of sampling, hence, in theory, should be treated as
   * different event schema _version_.
   */
  public static final int REPORT_ONLY_OPERATIONS_LONGER_THAN_MS =
    Integer.getInteger("IndexOperationFusCollector.REPORT_ONLY_OPERATIONS_LONGER_THAN_MS", 10);

  /**
   * How many recursive lookups to allow before suspect it is not a recursive lookup, but
   * just buggy code (missed {@linkplain LookupTraceBase#close()} call) and throw exception
   */
  public static final int MAX_LOOKUP_DEPTH = Integer.getInteger(
    "IndexOperationFusCollector.MAX_LOOKUP_DEPTH", 16);


  private static final EventLogGroup INDEX_USAGE_GROUP = new EventLogGroup("index.usage", 1);

  private static final EventLogGroup INDEX_USAGE_AGGREGATES_GROUP = new EventLogGroup("index.usage-aggregates", 1);

  // ================== EVENTS FIELDS:

  private static final StringEventField FIELD_INDEX_ID =
    EventFields.StringValidatedByCustomRule("index_id", IndexIdRuleValidator.class);

  private static final BooleanEventField FIELD_LOOKUP_FAILED = EventFields.Boolean("lookup_failed");

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
  private static final EnumEventField<LookupOperation> FIELD_LOOKUP_KEYS_OP =
    EventFields.Enum("lookup_op", LookupOperation.class, kind -> kind.name().toLowerCase(Locale.US));
  /**
   * How many keys (approximately) current index contains in total -- kind of 'lookup scale'
   */
  private static final IntEventField FIELD_TOTAL_KEYS_INDEXED_COUNT = EventFields.Int("total_keys_indexed");
  private static final IntEventField FIELD_LOOKUP_RESULT_ENTRIES_COUNT = EventFields.Int("entries_found");

  // ================== EVENTS:

  private static final VarargEventId EVENT_INDEX_ALL_KEYS_LOOKUP = INDEX_USAGE_GROUP.registerVarargEvent(
    "lookup.all_keys",
    FIELD_INDEX_ID,

    FIELD_LOOKUP_FAILED,

    FIELD_LOOKUP_DURATION_MS,          //LOOKUP_DURATION = (UP_TO_DATE_CHECK_DURATION) + (pure index lookup time)
    FIELD_UP_TO_DATE_CHECK_DURATION_MS,

    FIELD_TOTAL_KEYS_INDEXED_COUNT
    //LOOKUP_RESULT_ENTRIES_COUNT is useless here, since it == TOTAL_KEYS_INDEXED_COUNT
  );

  private static final VarargEventId EVENT_INDEX_LOOKUP_ENTRIES_BY_KEYS = INDEX_USAGE_GROUP.registerVarargEvent(
    "lookup.entries",
    FIELD_INDEX_ID,

    FIELD_LOOKUP_FAILED,

    FIELD_LOOKUP_DURATION_MS,       //LOOKUP_DURATION = (UP_TO_DATE_CHECK_DURATION) + (pure index lookup time)
    FIELD_UP_TO_DATE_CHECK_DURATION_MS,

    FIELD_LOOKUP_KEYS_COUNT,
    FIELD_LOOKUP_KEYS_OP,
    FIELD_TOTAL_KEYS_INDEXED_COUNT,
    FIELD_LOOKUP_RESULT_ENTRIES_COUNT
  );

  private static final VarargEventId EVENT_STUB_INDEX_LOOKUP_ENTRIES_BY_KEY = INDEX_USAGE_GROUP.registerVarargEvent(
    "lookup.stub_entries",
    FIELD_INDEX_ID,

    FIELD_LOOKUP_FAILED,

    //LOOKUP_DURATION = (UP_TO_DATE_CHECK_DURATION) + (pure index lookup time) + (STUB_TREE_DESERIALIZING_DURATION)
    FIELD_LOOKUP_DURATION_MS,
    FIELD_UP_TO_DATE_CHECK_DURATION_MS,
    FIELD_STUB_TREE_DESERIALIZING_DURATION_MS,

    //RC: StubIndex doesn't have methods to lookup >1 keys at once, so LOOKUP_KEYS_COUNT/LOOKUP_KEYS_OP is useless here
    FIELD_TOTAL_KEYS_INDEXED_COUNT,
    FIELD_LOOKUP_RESULT_ENTRIES_COUNT
  );

  // ================== IMPLEMENTATION METHODS:

  @Override
  public EventLogGroup getGroup() {
    return INDEX_USAGE_GROUP;
  }

  //========================== CLASSES:

  protected static abstract class LookupTraceBase<T extends LookupTraceBase<T>> implements AutoCloseable,
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
    protected @Nullable Project project;

    protected long lookupStartedAtMs;
    protected boolean lookupFailed;
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

    protected void setupTraceBeforeStart(@NotNull final IndexId<?, ?> indexId,
                                         @Nullable final T parentTrace) {
      this.indexId = indexId;
      this.project = null;
      this.lookupFailed = false;
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
        final long finishedAtMs = System.currentTimeMillis();
        final long lookupDurationMs = finishedAtMs - lookupStartedAtMs;
        if (lookupDurationMs > REPORT_ONLY_OPERATIONS_LONGER_THAN_MS || lookupFailed) {
          reportGatheredDataToAnalytics();
        }
      }
      finally {
        //indexId = null; //intentionally not clear it to provide a bit more debugging info
        project = null;//avoid keeping reference to project in a thread-locals

        if (parentTrace != null) {
          currentTraceHolder.set(parentTrace);
        }
        else {
          depth = -1;//since we re-use root trace object, we need to put it back to un-initialized state
          //or how we will know it is ready-to-use next time?
        }
      }
    }

    protected abstract void reportGatheredDataToAnalytics();

    @Override
    public final void close() {
      lookupFinished();
    }


    /* === Additional info about what was lookup-ed, and context/environment: ================================================ */


    public T withProject(final @Nullable Project project) {
      if (traceWasStarted()) {
        this.project = project;
      }
      return typeSafeThis();
    }

    public T lookupFailed() {
      if (traceWasStarted()) {
        this.lookupFailed = true;
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

    public String toString() {
      return getClass().getSimpleName() +
             "{indexId=" + indexId +
             ", project=" + project +
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

    @NotNull
    @SuppressWarnings("unchecked")
    private T typeSafeThis() {
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
  public static class LookupAllKeysTrace extends LookupTraceBase<LookupAllKeysTrace> {
    private long indexValidationFinishedAtMs;

    protected LookupAllKeysTrace(final ThreadLocal<LookupAllKeysTrace> current) {
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
    protected void reportGatheredDataToAnalytics() {
      final long lookupFinishedAtMs = System.currentTimeMillis();
      EVENT_INDEX_ALL_KEYS_LOOKUP.log(
        project,

        FIELD_INDEX_ID.with(indexId.getName()),

        //indexValidationFinishedAtMs==lookupStartedAtMs if not set due to exception
        // => UP_TO_DATE_CHECK_DURATION_MS would be 0 in that case
        FIELD_UP_TO_DATE_CHECK_DURATION_MS.with(indexValidationFinishedAtMs - lookupStartedAtMs),

        FIELD_LOOKUP_DURATION_MS.with(lookupFinishedAtMs - lookupStartedAtMs),

        FIELD_LOOKUP_FAILED.with(lookupFailed),

        FIELD_TOTAL_KEYS_INDEXED_COUNT.with(totalKeysIndexed)
      );
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
  public static class LookupEntriesByKeysTrace extends LookupTraceBase<LookupEntriesByKeysTrace> {
    private long indexValidationFinishedAtMs;

    /**
     * How many keys were looked up (-1 => 'unknown')
     */
    private int lookupKeysCount = -1;
    private LookupOperation lookupOperation = LookupOperation.UNKNOWN;

    protected LookupEntriesByKeysTrace(final ThreadLocal<LookupEntriesByKeysTrace> current) {
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
    protected void reportGatheredDataToAnalytics() {
      final long lookupFinishedAtMs = System.currentTimeMillis();
      EVENT_INDEX_LOOKUP_ENTRIES_BY_KEYS.log(
        project,

        FIELD_INDEX_ID.with(indexId.getName()),

        FIELD_UP_TO_DATE_CHECK_DURATION_MS.with(
          indexValidationFinishedAtMs > 0 ? indexValidationFinishedAtMs - lookupStartedAtMs : 0),

        FIELD_LOOKUP_DURATION_MS.with(lookupFinishedAtMs - lookupStartedAtMs),

        FIELD_LOOKUP_FAILED.with(lookupFailed),

        FIELD_LOOKUP_KEYS_OP.with(lookupOperation),
        FIELD_LOOKUP_KEYS_COUNT.with(lookupKeysCount),

        FIELD_TOTAL_KEYS_INDEXED_COUNT.with(totalKeysIndexed),
        FIELD_LOOKUP_RESULT_ENTRIES_COUNT.with(lookupResultSize)
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
  public static class LookupStubEntriesByKeyTrace extends LookupTraceBase<LookupStubEntriesByKeyTrace> {
    //total lookup time = (upToDateCheck time) + (pure index lookup time) + (Stub Trees deserializing time)
    private long indexValidationFinishedAtMs;
    private long stubTreesDeserializingStarted;

    protected LookupStubEntriesByKeyTrace(final ThreadLocal<LookupStubEntriesByKeyTrace> current) {
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
    protected void reportGatheredDataToAnalytics() {
      final long lookupFinishedAtMs = System.currentTimeMillis();
      EVENT_STUB_INDEX_LOOKUP_ENTRIES_BY_KEY.log(
        project,

        FIELD_INDEX_ID.with(indexId.getName()),

        FIELD_UP_TO_DATE_CHECK_DURATION_MS.with(
          indexValidationFinishedAtMs > 0 ? indexValidationFinishedAtMs - lookupStartedAtMs : 0),

        FIELD_STUB_TREE_DESERIALIZING_DURATION_MS.with(
          stubTreesDeserializingStarted > 0 ? lookupFinishedAtMs - stubTreesDeserializingStarted : 0),

        FIELD_LOOKUP_DURATION_MS.with(lookupFinishedAtMs - lookupStartedAtMs),

        FIELD_LOOKUP_FAILED.with(lookupFailed),

        FIELD_TOTAL_KEYS_INDEXED_COUNT.with(totalKeysIndexed),
        FIELD_LOOKUP_RESULT_ENTRIES_COUNT.with(lookupResultSize)
      );
    }
  }

  public static LookupStubEntriesByKeyTrace lookupStubEntriesStarted(final IndexId<?, ?> indexId) {
    return TRACE_OF_STUB_ENTRIES_LOOKUP.get().lookupStarted(indexId);
  }
}
