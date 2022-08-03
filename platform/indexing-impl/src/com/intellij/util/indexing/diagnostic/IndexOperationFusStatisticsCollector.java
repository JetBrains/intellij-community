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
 * @author cheremin.ruslan
 * created 20.07.22 at 18:20
 */
public class IndexOperationFusStatisticsCollector extends CounterUsagesCollector {
  private static final Logger LOG = Logger.getInstance(IndexOperationFusStatisticsCollector.class);

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


  //FIXME RC: YT to register new group: https://youtrack.jetbrains.com/issue/FUS-1818/Add-new-group-cloudprojectsbranches
  private static final EventLogGroup GROUP = new EventLogGroup("index.usage", 1);

  // ================== EVENTS FIELDS:

  private static final StringEventField INDEX_NAME_FIELD =
    new StringEventField.ValidatedByCustomValidationRule("index_name", IndexIDValidationRule.class);

  private static final BooleanEventField LOOKUP_FAILED = new BooleanEventField("lookup-failed");

  /**
   * Total lookup time, as it is seen by 'client' (i.e. including up-to-date/validation, and stubs deserializing, etc...)
   */
  private static final LongEventField LOOKUP_DURATION_MS = new LongEventField("lookup-duration-ms");
  private static final LongEventField UP_TO_DATE_CHECK_DURATION_MS = new LongEventField("up-to-date-check-ms");
  private static final LongEventField STUB_TREE_DESERIALIZING_DURATION_MS = new LongEventField("psi-tree-deserializing-ms");

  /**
   * How many keys were lookup-ed (there are methods to lookup >1 keys at once)
   */
  private static final IntEventField LOOKUP_KEYS_COUNT = new IntEventField("keys");
  /**
   * For cases >1 keys lookup: what operation is applied (AND/OR)
   */
  private static final EnumEventField<LookupOperation> LOOKUP_KEYS_OP =
    new EnumEventField<>("lookup-op", LookupOperation.class, kind -> kind.name().toLowerCase());
  /**
   * How many keys (approximately) current index contains in total -- kind of 'lookup scale'
   */
  private static final IntEventField TOTAL_KEYS_INDEXED_COUNT = new IntEventField("total-keys-indexed");

  // ================== EVENTS:

  private static final VarargEventId INDEX_ALL_KEYS_LOOKUP = GROUP.registerVarargEvent(
    "lookup.all-keys",
    INDEX_NAME_FIELD,

    LOOKUP_FAILED,

    //LOOKUP_DURATION_MS = (UP_TO_DATE_CHECK_DURATION_MS) + (pure index lookup time)
    LOOKUP_DURATION_MS,
    UP_TO_DATE_CHECK_DURATION_MS,

    TOTAL_KEYS_INDEXED_COUNT
  );

  private static final VarargEventId INDEX_VALUES_LOOKUP = GROUP.registerVarargEvent(
    "lookup.values",
    INDEX_NAME_FIELD,

    LOOKUP_FAILED,

    //LOOKUP_DURATION_MS = (UP_TO_DATE_CHECK_DURATION_MS) + (pure index lookup time)
    LOOKUP_DURATION_MS,
    UP_TO_DATE_CHECK_DURATION_MS,

    LOOKUP_KEYS_COUNT,
    LOOKUP_KEYS_OP,
    TOTAL_KEYS_INDEXED_COUNT
  );

  private static final VarargEventId STUB_INDEX_VALUES_LOOKUP = GROUP.registerVarargEvent(
    "lookup.stub-values",
    INDEX_NAME_FIELD,

    LOOKUP_FAILED,

    //LOOKUP_DURATION_MS = (UP_TO_DATE_CHECK_DURATION_MS) + (pure index lookup time) + (STUB_TREE_DESERIALIZING_DURATION_MS)
    LOOKUP_DURATION_MS,
    UP_TO_DATE_CHECK_DURATION_MS,
    STUB_TREE_DESERIALIZING_DURATION_MS,

    //RC: StubIndex doesn't have methods to lookup >1 keys at once, so LOOKUP_KEYS_COUNT/LOOKUP_KEYS_OP is useless here
    TOTAL_KEYS_INDEXED_COUNT
  );

  // ================== IMPLEMENTATION METHODS:

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  //========================== CLASSES:

  public static class IndexIDValidationRule extends CustomValidationRule {
    //TODO RC: should RULE_ID be globally unique? There is already a rule 'index_id' in SharedIndexesFusCollector,
    //         could it conflict with the current one?
    public static final String RULE_ID = "index_id";

    @NotNull
    @Override
    public String getRuleId() {
      return RULE_ID;
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(final @NotNull String indexId, final @NotNull EventContext context) {
      //FIXME RC: allow all for prototyping, but for real -- CustomValidationRule to accept only index names
      //TODO RC: how to really check string is and ID of existing index?

      return ValidationResultType.ACCEPTED;
    }
  }

  private static abstract class LookupTraceBase<T extends LookupTraceBase<T>> implements AutoCloseable {
    protected boolean traceWasStarted = false;
    protected @Nullable IndexId<?, ?> indexId;
    protected @Nullable Project project;

    protected long lookupStartedAtMs;
    protected boolean lookupFailed;
    protected int totalKeysIndexed;

    protected T lookupStarted(final IndexId<?, ?> indexId) {
      ensureNotYetStarted();
      //if not thrown -> and continue as-if no previous trace exists, i.e. overwrite all data remaining from unfinished trace

      this.indexId = indexId;
      this.project = null;
      this.lookupFailed = false;
      this.totalKeysIndexed = -1;

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
        if (lookupDurationMs > REPORT_ONLY_OPERATIONS_LONGER_THAN_MS) {
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
      mustBeStarted();
      this.project = project;
      return (T)this;
    }

    public T lookupFailed() {
      mustBeStarted();
      this.lookupFailed = true;
      return (T)this;
    }

    public T totalKeysIndexed(final int totalKeysIndexed) {
      mustBeStarted();
      this.totalKeysIndexed = totalKeysIndexed;
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
      mustBeStarted();
      indexValidationFinishedAtMs = System.currentTimeMillis();
      return this;
    }

    @Override
    protected void reportGatheredDataToAnalytics() {
      final long lookupFinishedAtMs = System.currentTimeMillis();
      INDEX_ALL_KEYS_LOOKUP.log(
        project,

        INDEX_NAME_FIELD.with(indexId.getName()),

        //indexValidationFinishedAtMs==lookupStartedAtMs if not set due to exception
        // => UP_TO_DATE_CHECK_DURATION_MS would be 0 in that case
        UP_TO_DATE_CHECK_DURATION_MS.with(indexValidationFinishedAtMs - lookupStartedAtMs),

        LOOKUP_DURATION_MS.with(lookupFinishedAtMs - lookupStartedAtMs),

        LOOKUP_FAILED.with(lookupFailed),

        TOTAL_KEYS_INDEXED_COUNT.with(totalKeysIndexed));
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
      mustBeStarted();
      indexValidationFinishedAtMs = System.currentTimeMillis();
      return this;
    }

    @Override
    protected void reportGatheredDataToAnalytics() {
      final long lookupFinishedAtMs = System.currentTimeMillis();
      INDEX_VALUES_LOOKUP.log(
        project,

        INDEX_NAME_FIELD.with(indexId.getName()),

        UP_TO_DATE_CHECK_DURATION_MS.with(
          indexValidationFinishedAtMs > 0 ? indexValidationFinishedAtMs - lookupStartedAtMs : 0),

        LOOKUP_DURATION_MS.with(lookupFinishedAtMs - lookupStartedAtMs),

        LOOKUP_FAILED.with(lookupFailed),

        LOOKUP_KEYS_OP.with(lookupOperation), LOOKUP_KEYS_COUNT.with(lookupKeysCount),
        TOTAL_KEYS_INDEXED_COUNT.with(totalKeysIndexed));
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
      mustBeStarted();
      indexValidationFinishedAtMs = System.currentTimeMillis();
      return this;
    }

    public StubValuesLookupTrace stubTreesDeserializingStarted() {
      mustBeStarted();
      stubTreesDeserializingStarted = System.currentTimeMillis();
      return this;
    }

    @Override
    protected void reportGatheredDataToAnalytics() {
      final long lookupFinishedAtMs = System.currentTimeMillis();
      STUB_INDEX_VALUES_LOOKUP.log(
        project,

        INDEX_NAME_FIELD.with(indexId.getName()),

        UP_TO_DATE_CHECK_DURATION_MS.with(
          indexValidationFinishedAtMs > 0 ? indexValidationFinishedAtMs - lookupStartedAtMs : 0),

        STUB_TREE_DESERIALIZING_DURATION_MS.with(
          stubTreesDeserializingStarted > 0 ? lookupFinishedAtMs - stubTreesDeserializingStarted : 0),

        LOOKUP_DURATION_MS.with(lookupFinishedAtMs - lookupStartedAtMs),

        LOOKUP_FAILED.with(lookupFailed),

        TOTAL_KEYS_INDEXED_COUNT.with(totalKeysIndexed));
    }
  }

  public static StubValuesLookupTrace stubValuesLookupStarted(final IndexId<?, ?> indexId) {
    return TRACE_OF_STUB_VALUES_LOOKUP.get().lookupStarted(indexId);
  }
}
