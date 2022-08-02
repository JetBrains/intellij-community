// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.IndexId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cheremin.ruslan
 * created 20.07.22 at 18:20
 */
public class IndexOperationFusStatisticsCollector extends CounterUsagesCollector {

  //FIXME RC: For real group name better to be like 'index-lookup'/'index-usage', and then event names
  //          like 'lookup-all-keys', 'lookup-values'
  //          E.g. YT to register new group: https://youtrack.jetbrains.com/issue/FUS-1818/Add-new-group-cloudprojectsbranches
  private static final EventLogGroup GROUP = new EventLogGroup(
    "test.index_usage",
    1
  );

  // ================== EVENTS FIELDS:

  private static final StringEventField INDEX_NAME_FIELD = new StringEventField.ValidatedByCustomValidationRule(
    "index_name",
    IndexIDValidationRule.class
  );

  private static final LongEventField UP_TO_DATE_CHECK_DURATION_MS = new LongEventField("up-to-date-check-duration-ms");
  private static final BooleanEventField LOOKUP_FAILED = new BooleanEventField("lookup-failed");
  private static final LongEventField LOOKUP_DURATION_MS = new LongEventField("lookup-duration-ms");

  private static final IntEventField LOOKUP_KEYS_COUNT = new IntEventField("keys");
  private static final IntEventField TOTAL_KEYS_INDEXED_COUNT = new IntEventField("total-keys-indexed");
  private static final EnumEventField<LookupOperation> LOOKUP_KEYS_OP = new EnumEventField<>(
    "lookup-op",
    LookupOperation.class,
    kind -> kind.name().toLowerCase()
  );

  // ================== EVENTS:
  private static final VarargEventId INDEX_ALL_KEYS_LOOKUP = GROUP.registerVarargEvent(
    "lookup.all-keys",
    INDEX_NAME_FIELD,
    UP_TO_DATE_CHECK_DURATION_MS,
    LOOKUP_DURATION_MS,
    TOTAL_KEYS_INDEXED_COUNT
  );

  private static final VarargEventId INDEX_VALUES_LOOKUP = GROUP.registerVarargEvent(
    "lookup.values",
    INDEX_NAME_FIELD,
    UP_TO_DATE_CHECK_DURATION_MS,
    LOOKUP_FAILED,
    LOOKUP_DURATION_MS,
    LOOKUP_KEYS_COUNT,
    LOOKUP_KEYS_OP,
    TOTAL_KEYS_INDEXED_COUNT
  );

  // ================== IMPLEMENTATION METHODS:

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void indexAllKeysLookupFinished(final String indexName,
                                                final long upToDateCheckDurationMs,
                                                final long lookupDurationMs,
                                                final int totalKeysCount) {
    INDEX_ALL_KEYS_LOOKUP.log(
      INDEX_NAME_FIELD.with(indexName),
      UP_TO_DATE_CHECK_DURATION_MS.with(upToDateCheckDurationMs),
      LOOKUP_DURATION_MS.with(lookupDurationMs),
      TOTAL_KEYS_INDEXED_COUNT.with(totalKeysCount)
    );
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
    protected ValidationResultType doValidate(final @NotNull String indexId,
                                              final @NotNull EventContext context) {
      //FIXME RC: allow all for prototyping, but for real -- CustomValidationRule to accept only index names
      //TODO RC: how to really check string is and ID of existing index?

      return ValidationResultType.ACCEPTED;
    }
  }

  public static final ThreadLocal<ValuesLookupTrace> TRACE_OF_VALUES_LOOKUP = ThreadLocal.withInitial(ValuesLookupTrace::new);

  /**
   * Holds a trace (timestamps, pieces of data) for a 'lookup values' index query. To be used as thread-local
   * object.
   */
  public static class ValuesLookupTrace implements AutoCloseable {
    private boolean traceWasStarted = false;
    private @Nullable IndexId<?, ?> indexId;
    private @Nullable Project project;
    private long lookupStartedAtMs;
    private long indexValidationFinishedAtMs;

    /**
     * How many keys were looked up (-1 => 'unknown')
     */
    private int lookupKeysCount = -1;
    private int totalKeysIndexed;

    private LookupOperation lookupOperation = LookupOperation.UNKNOWN;
    private boolean lookupFailed;


    public ValuesLookupTrace logLookupStarted(final @NotNull IndexId<?, ?> indexId) {
      //TODO RC: generally it is not a good idea to throw exception during analytics. Useful for
      // debugging, but better to be switchable with flag like THROW_ON_INCORRECT_USAGE

      assert !traceWasStarted : ".logQueryStarted() was called, but not paired with .logQueryFinished() yet. " + this;

      this.indexId = indexId;
      this.project = null;
      this.lookupFailed = false;
      this.lookupOperation = LookupOperation.UNKNOWN;
      this.lookupKeysCount = -1;
      this.totalKeysIndexed = -1;

      traceWasStarted = true;
      lookupStartedAtMs = System.currentTimeMillis();
      this.indexValidationFinishedAtMs = lookupStartedAtMs;

      return this;
    }

    public ValuesLookupTrace logIndexValidationFinished() {
      if (traceWasStarted) {
        indexValidationFinishedAtMs = System.currentTimeMillis();
      }
      return this;
    }

    public void logLookupFinished() {
      assert traceWasStarted : ".logLookupStarted() must be called first! " + this;
      final long lookupFinishedAtMs = System.currentTimeMillis();

      try {
        //TODO RC: don't need to log each event with lookup time = 0, but it is worth to count how many
        //         such events are in total, otherwise we wouldn't be able to measure improvements
        //         between versions
        INDEX_VALUES_LOOKUP.log(
          project,

          INDEX_NAME_FIELD.with(indexId.getName()),

          //indexValidationFinishedAtMs==lookupStartedAtMs if not set due to exception
          // => UP_TO_DATE_CHECK_DURATION_MS would be 0 in that case
          UP_TO_DATE_CHECK_DURATION_MS.with(indexValidationFinishedAtMs - lookupStartedAtMs),

          LOOKUP_DURATION_MS.with(lookupFinishedAtMs - lookupStartedAtMs),

          LOOKUP_FAILED.with(lookupFailed),

          LOOKUP_KEYS_OP.with(lookupOperation),
          LOOKUP_KEYS_COUNT.with(lookupKeysCount),
          TOTAL_KEYS_INDEXED_COUNT.with(totalKeysIndexed)
        );
      }
      finally {
        traceWasStarted = false;
        indexId = null;
        project = null;
        lookupFailed = false;
        lookupKeysCount = -1;//unknown
        lookupOperation = LookupOperation.UNKNOWN;
      }
    }

    @Override
    public void close() { //to be used in try-with-resources 
      logLookupFinished();
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

    public ValuesLookupTrace withProject(final @Nullable Project project) {
      this.project = project;
      return this;
    }

    public ValuesLookupTrace lookupFailed() {
      this.lookupFailed = true;
      return this;
    }

    public ValuesLookupTrace totalKeysIndexed(final int totalKeysIndexed) {
      this.totalKeysIndexed = totalKeysIndexed;
      return this;
    }

    public String toString() {
      return "{indexId=" + indexId +
             ", project=" + project +
             ", is started? =" + traceWasStarted +
             ", queryStartedAtMs=" + lookupStartedAtMs +
             ", validationFinishedAtMs=" + indexValidationFinishedAtMs +
             '}';
    }
  }

  public static ValuesLookupTrace logValuesLookupStarted(final IndexId<?, ?> indexId) {
    return TRACE_OF_VALUES_LOOKUP.get().logLookupStarted(indexId);
  }

  enum LookupOperation {AND, OR, UNKNOWN}
}
