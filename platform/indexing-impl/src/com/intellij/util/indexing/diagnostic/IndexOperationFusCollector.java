// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.IndexId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Collects and reports performance data (timings mostly) about index usage (lookup). Right now there 'all keys'
 * lookups and 'value(s) by key(s)' lookups are timed and reported with some additional infos.
 *
 */
public class IndexOperationFusCollector extends CounterUsagesCollector {
  private static final Logger LOG = Logger.getInstance(IndexOperationFusCollector.class);

  /**
   * If true -> throw exception if tracing methods are called in incorrect order (e.g. .finish() before start()).
   * if false (default) -> log warning on incorrect sequence of calls, but try to continue normal operation afterwards.
   * <p>
   * Really, value=true is only useful for debugging -- in production reporting are generally not expected to throw exceptions.
   */
  public static final boolean THROW_ON_INCORRECT_USAGE =
    Boolean.getBoolean("IndexOperationFusStatisticsCollector.THROW_ON_INCORRECT_USAGE");

  /**
   * Report lookup operation X to analytics only if total duration of the operation X {@code >REPORT_ONLY_OPERATIONS_LONGER_THAN_MS}.
   * There are a lot of index lookups, and this threshold allows to reduce reporting traffic, since we're really only interested in
   * long operations. Default value 0 means 'report lookups >0ms only'
   */
  public static final int REPORT_ONLY_OPERATIONS_LONGER_THAN_MS =
    Integer.getInteger("IndexOperationFusStatisticsCollector.THROW_ON_INCORRECT_USAGE", 10);


  private static final EventLogGroup INDEX_USAGE_GROUP = new EventLogGroup("index.usage", 1);

  // ================== EVENTS FIELDS:

  private static final StringEventField INDEX_ID_FIELD =
    new StringEventField.ValidatedByCustomValidationRule("index-id", IndexIdRuleValidator.class);

  private static final BooleanEventField LOOKUP_FAILED_FIELD = new BooleanEventField("lookup-failed");

  /**
   * Total lookup time, as it is seen by 'client' (i.e. including up-to-date/validation, and stubs deserializing, etc...)
   */
  private static final LongEventField LOOKUP_DURATION_MS_FIELD = new LongEventField("lookup-duration-ms");
  private static final LongEventField UP_TO_DATE_CHECK_DURATION_MS_FIELD = new LongEventField("up-to-date-check-ms");
  private static final LongEventField STUB_TREE_DESERIALIZING_DURATION_MS_FIELD = new LongEventField("psi-tree-deserializing-ms");

  /**
   * How many keys were lookup-ed (there are methods to lookup >1 keys at once)
   */
  private static final IntEventField LOOKUP_KEYS_COUNT_FIELD = new IntEventField("keys");
  /**
   * For cases >1 keys lookup: what operation is applied (AND/OR)
   */
  private static final EnumEventField<LookupOperation> LOOKUP_KEYS_OP_FIELD =
    new EnumEventField<>("lookup-op", LookupOperation.class, kind -> kind.name().toLowerCase());
  /**
   * How many keys (approximately) current index contains in total -- kind of 'lookup scale'
   */
  private static final IntEventField TOTAL_KEYS_INDEXED_COUNT_FIELD = new IntEventField("total-keys-indexed");
  private static final IntEventField LOOKUP_RESULT_VALUES_COUNT_FIELD = new IntEventField("values-found");

  // ================== EVENTS:

  private static final VarargEventId INDEX_ALL_KEYS_LOOKUP_EVENT = INDEX_USAGE_GROUP.registerVarargEvent(
    "lookup.all-keys",
    INDEX_ID_FIELD,

    LOOKUP_FAILED_FIELD,

    LOOKUP_DURATION_MS_FIELD,          //LOOKUP_DURATION = (UP_TO_DATE_CHECK_DURATION) + (pure index lookup time)
    UP_TO_DATE_CHECK_DURATION_MS_FIELD,

    TOTAL_KEYS_INDEXED_COUNT_FIELD
    //LOOKUP_RESULT_VALUES_COUNT is useless here, since it == TOTAL_KEYS_INDEXED_COUNT
  );

  private static final VarargEventId INDEX_VALUES_LOOKUP_EVENT = INDEX_USAGE_GROUP.registerVarargEvent(
    "lookup.values",
    INDEX_ID_FIELD,

    LOOKUP_FAILED_FIELD,

    LOOKUP_DURATION_MS_FIELD,       //LOOKUP_DURATION = (UP_TO_DATE_CHECK_DURATION) + (pure index lookup time)
    UP_TO_DATE_CHECK_DURATION_MS_FIELD,

    LOOKUP_KEYS_COUNT_FIELD,
    LOOKUP_KEYS_OP_FIELD,
    TOTAL_KEYS_INDEXED_COUNT_FIELD,
    LOOKUP_RESULT_VALUES_COUNT_FIELD
  );

  private static final VarargEventId STUB_INDEX_VALUES_LOOKUP_EVENT = INDEX_USAGE_GROUP.registerVarargEvent(
    "lookup.stub-values",
    INDEX_ID_FIELD,

    LOOKUP_FAILED_FIELD,

    //LOOKUP_DURATION = (UP_TO_DATE_CHECK_DURATION) + (pure index lookup time) + (STUB_TREE_DESERIALIZING_DURATION)
    LOOKUP_DURATION_MS_FIELD,
    UP_TO_DATE_CHECK_DURATION_MS_FIELD,
    STUB_TREE_DESERIALIZING_DURATION_MS_FIELD,

    //RC: StubIndex doesn't have methods to lookup >1 keys at once, so LOOKUP_KEYS_COUNT/LOOKUP_KEYS_OP is useless here
    TOTAL_KEYS_INDEXED_COUNT_FIELD,
    LOOKUP_RESULT_VALUES_COUNT_FIELD
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

    protected T lookupStarted(final IndexId<?, ?> indexId) {
      ensureNotYetStarted();
      //if not thrown -> and continue as-if no previous trace exists, i.e. overwrite all data remaining from unfinished trace

      this.indexId = indexId;
      this.project = null;
      this.lookupFailed = false;
      this.totalKeysIndexed = -1;
      this.lookupResultSize = -1;

      traceWasStarted = true;
      lookupStartedAtMs = System.currentTimeMillis();
      return (T)this;
    }

    public void lookupFinished() {
      if (!mustBeStarted()) {
        //if trace wasn't started -> nothing (meaningful) to report
        return;
      }

      try {
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
      return (T)this;
    }

    public T lookupFailed() {
      if (traceWasStarted) {
        this.lookupFailed = true;
      }
      return (T)this;
    }

    public T totalKeysIndexed(final int totalKeysIndexed) {
      if (traceWasStarted) {
        this.totalKeysIndexed = totalKeysIndexed;
      }
      return (T)this;
    }

    public T lookupResultSize(final int size) {
      if (traceWasStarted) {
        this.lookupResultSize = size;
      }
      return (T)this;
    }

    private void ensureNotYetStarted() {
      if (traceWasStarted) {
        final String errorMessage = "Code bug: .logQueryStarted() was called, but not paired with .logQueryFinished() yet. " + this;
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

  public static final ThreadLocal<AllKeysLookupTrace> TRACE_OF_ALL_KEYS_LOOKUP = ThreadLocal.withInitial(AllKeysLookupTrace::new);

  /**
   * Holds a trace (timestamps, pieces of data) for a 'lookup values' index query. To be used as thread-local
   * object.
   */
  public static class AllKeysLookupTrace extends LookupTraceBase<AllKeysLookupTrace> {
    private long indexValidationFinishedAtMs;

    public AllKeysLookupTrace lookupStarted(final @NotNull IndexId<?, ?> indexId) {
      super.lookupStarted(indexId);
      this.indexValidationFinishedAtMs = -1;

      return this;
    }

    public AllKeysLookupTrace indexValidationFinished() {
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

  public static AllKeysLookupTrace allKeysLookupStarted(final IndexId<?, ?> indexId) {
    return TRACE_OF_ALL_KEYS_LOOKUP.get().lookupStarted(indexId);
  }


  //========================== Values lookup reporting:

  public static final ThreadLocal<ValuesLookupTrace> TRACE_OF_VALUES_LOOKUP = ThreadLocal.withInitial(ValuesLookupTrace::new);

  /**
   * Holds a trace (timestamps, pieces of data) for a 'lookup values' index query. To be used as thread-local
   * object.
   */
  public static class ValuesLookupTrace extends LookupTraceBase<ValuesLookupTrace> {
    private long indexValidationFinishedAtMs;

    /**
     * How many keys were looked up (-1 => 'unknown')
     */
    private int lookupKeysCount = -1;
    private LookupOperation lookupOperation = LookupOperation.UNKNOWN;


    public ValuesLookupTrace lookupStarted(final @NotNull IndexId<?, ?> indexId) {
      super.lookupStarted(indexId);

      this.lookupOperation = LookupOperation.UNKNOWN;
      this.lookupKeysCount = -1;
      this.indexValidationFinishedAtMs = -1;

      return this;
    }

    public ValuesLookupTrace indexValidationFinished() {
      if (traceWasStarted) {
        indexValidationFinishedAtMs = System.currentTimeMillis();
      }
      return this;
    }

    @Override
    protected void reportGatheredDataToAnalytics() {
      final long lookupFinishedAtMs = System.currentTimeMillis();
      INDEX_VALUES_LOOKUP_EVENT.log(
        project,

        INDEX_ID_FIELD.with(indexId.getName()),

        UP_TO_DATE_CHECK_DURATION_MS_FIELD.with(
          indexValidationFinishedAtMs > 0 ? indexValidationFinishedAtMs - lookupStartedAtMs : 0),

        LOOKUP_DURATION_MS_FIELD.with(lookupFinishedAtMs - lookupStartedAtMs),

        LOOKUP_FAILED_FIELD.with(lookupFailed),

        LOOKUP_KEYS_OP_FIELD.with(lookupOperation),
        LOOKUP_KEYS_COUNT_FIELD.with(lookupKeysCount),

        TOTAL_KEYS_INDEXED_COUNT_FIELD.with(totalKeysIndexed),
        LOOKUP_RESULT_VALUES_COUNT_FIELD.with(lookupResultSize)
      );
    }

    //=== Additional info about what was lookup-ed, and context/environment:

    public ValuesLookupTrace keysWithAND(final int keysCount) {
      this.lookupKeysCount = keysCount;
      this.lookupOperation = LookupOperation.AND;
      return this;
    }

    public ValuesLookupTrace keysWithOR(final int keysCount) {
      this.lookupKeysCount = keysCount;
      this.lookupOperation = LookupOperation.OR;
      return this;
    }
  }

  public static ValuesLookupTrace valuesLookupStarted(final IndexId<?, ?> indexId) {
    return TRACE_OF_VALUES_LOOKUP.get().lookupStarted(indexId);
  }

  enum LookupOperation {AND, OR, UNKNOWN}

  //========================== Stub-Index Values lookup reporting:

  public static final ThreadLocal<StubValuesLookupTrace> TRACE_OF_STUB_VALUES_LOOKUP = ThreadLocal.withInitial(StubValuesLookupTrace::new);

  /**
   * Holds a trace (timestamps, pieces of data) for a 'lookup values' index query. To be used as thread-local
   * object.
   */
  public static class StubValuesLookupTrace extends LookupTraceBase<StubValuesLookupTrace> {
    //total lookup time = (upToDateCheck time) + (pure index lookup time) + (Stub Trees deserializing time)
    private long indexValidationFinishedAtMs;
    private long stubTreesDeserializingStarted;

    public StubValuesLookupTrace lookupStarted(final @NotNull IndexId<?, ?> indexId) {
      super.lookupStarted(indexId);

      indexValidationFinishedAtMs = -1;
      stubTreesDeserializingStarted = -1;

      return this;
    }

    public StubValuesLookupTrace indexValidationFinished() {
      if (traceWasStarted) {
        indexValidationFinishedAtMs = System.currentTimeMillis();
      }
      return this;
    }

    public StubValuesLookupTrace stubTreesDeserializingStarted() {
      if (traceWasStarted) {
        stubTreesDeserializingStarted = System.currentTimeMillis();
      }
      return this;
    }

    @Override
    protected void reportGatheredDataToAnalytics() {
      final long lookupFinishedAtMs = System.currentTimeMillis();
      STUB_INDEX_VALUES_LOOKUP_EVENT.log(
        project,

        INDEX_ID_FIELD.with(indexId.getName()),

        UP_TO_DATE_CHECK_DURATION_MS_FIELD.with(
          indexValidationFinishedAtMs > 0 ? indexValidationFinishedAtMs - lookupStartedAtMs : 0),

        STUB_TREE_DESERIALIZING_DURATION_MS_FIELD.with(
          stubTreesDeserializingStarted > 0 ? lookupFinishedAtMs - stubTreesDeserializingStarted : 0),

        LOOKUP_DURATION_MS_FIELD.with(lookupFinishedAtMs - lookupStartedAtMs),

        LOOKUP_FAILED_FIELD.with(lookupFailed),

        TOTAL_KEYS_INDEXED_COUNT_FIELD.with(totalKeysIndexed),
        LOOKUP_RESULT_VALUES_COUNT_FIELD.with(lookupResultSize)
      );
    }
  }

  public static StubValuesLookupTrace stubValuesLookupStarted(final IndexId<?, ?> indexId) {
    return TRACE_OF_STUB_VALUES_LOOKUP.get().lookupStarted(indexId);
  }
}
