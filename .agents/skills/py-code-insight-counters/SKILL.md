---
name: py-code-insight-counters
description: Count Python code insight work in tests with PyCodeInsightCounters.
---

# Python code insight counters

`PyCodeInsightCounters` (`python-psi-impl`, package `com.jetbrains.python.codeInsight`) counts the work of the native
Python type engine. `PyPerfProbe` (`community/python/testFramework`, package `com.intellij.python.community.testFramework.performance`)
measures a scenario with these counters, the wall time, the allocations and the AST loads.

Use them to show that a change does less work, to show how the work grows with the input, or to pin a performance
defect in a test. A work count does not flake like a time.

## Counters

| Counter | Counts | Hook |
| --- | --- | --- |
| `GET_TYPE_CALLS` | each `getType` call | `TypeEvalContextImpl.getType` |
| `GET_TYPE_CACHE_HITS` | the calls that the context cache answers | `TypeEvalContextImpl.getType` |
| `GET_TYPE_EVALUATIONS` | the calls that evaluate a type | `TypeEvalContextImpl.getType` |
| `CONTEXTS_CONSTRUCTED` | each type context, also the library and assumption contexts | `TypeEvalContextImpl` constructor |
| `CONTEXT_LOOKUP_MISSES` | the context lookups that store a new context | `TypeEvalContextCacheImpl` |
| `ASSUME_TYPE_CALLS` | the narrowing assumptions that create a context | `TypeEvalContextImpl.assumeType` |
| `MATCH_STEPS` | the steps of the type match | `PyTypeChecker.match` |
| `OVERLOAD_CANDIDATES_CHECKED` | the overload candidates that the argument types check | `PyCallExpressionHelper.matchesByArgumentTypes` |
| `CFG_BUILDS`, `CFG_INSTRUCTIONS` | the control flow builds and their instructions | `PyControlFlowBuilder.buildControlFlow` |
| `COMPLETION_LOOKUP_ITEMS` | the lookup items of the Python completion | `CompletionVariantsProcessor.setupItem` |

A cache hit and an evaluation do not add up to the calls. The rest are library delegations and the requests that the
recursion guard stops.

## Count in a test

The counting is off by default. For the work of the calling thread only:

```kotlin
val counts = PyCodeInsightCounters.countOnCurrentThread { context.getType(expression) }
assertEquals(1L, counts.getValue(Counter.GET_TYPE_EVALUATIONS))
```

The highlighting passes run on other threads, so `countOnCurrentThread` does not see their work. For all threads, take
two snapshots:

```kotlin
PyCodeInsightCounters.enable(testRootDisposable)
val before = PyCodeInsightCounters.snapshot()
myFixture.doHighlighting()
val delta = PyCodeInsightCounters.snapshot() - before
assertTrue(delta[Counter.CFG_BUILDS] >= 1)
```

The delta also contains the background work of the IDE in that time. Assert an exact count only for the calling thread.
Otherwise assert a bound or a ratio.

## Gate a scenario

A gate is a normal test that fails on a performance regression. It runs on each Safe Push, so it must be fast and
must not depend on the machine.

1. In a `PyCodeInsightTestCase`, register the probe extension:
   `@JvmField @RegisterExtension val perf = PyPerfProbeExtension { myFixture }`. It gives each test its own probe.
   The probe turns the counting on, turns off the `RecursionManager` test checks and sets the registry keys of
   `PyPerfProbe.PINNED_REGISTRY` to their IDE values.
2. `perf.probe.work { action }` runs the action once after `cold()`. It returns the counters of the calling thread, the
   counters of all threads and the AST loads.
3. Check the growth of a counter with `assertGrowth(scenario, sizes, PyGrowth.LINEAR) { size -> ... }`, or a fact
   with `assertExpectation`. Use small sizes (n, 2n, 4n). The growth is the exponent between the two largest sizes.
4. When an open issue makes the expectation fail, give `PyKnownDefect(issue, growth)`. The gate then fails when the
   work is worse than the defect, and also when the expectation holds. So the fix of the issue updates the gate.

`PyCodeInsightScalingPerformanceTest` and `PyEditorFeaturesPerformanceTest` are examples.

## Benchmark a scenario

A benchmark is a `@PerformanceUnitTest` method. The usual test runs skip it, and the performance test builds of
TeamCity run it. `tests.cmd` runs it when you give its fully qualified name.

1. Call one of these:
   - `measure(scenario) { action }` runs 3 warm-up attempts and 10 attempts. Before each attempt, `setup` runs.
     The default `setup` is `cold()`.
   - `measureEditorFile(name, inspections)` measures a cold highlighting, a highlighting after a one-character edit,
     and one `inferAll` pass.
   - `measureScaled(scenario) { action }` uses fewer attempts when the first run takes more than 10 seconds.
2. `measure` runs the attempts as the IJ Perf `Benchmark` subtest `scenario` of the calling test method. Call it from
   the test method, not from another thread. The benchmark publishes the time and the median counters, allocations and
   AST loads (`counter.<id>`, `alloc.*.kb`, `astLoads.firstAttempt.*`). The IJ Perf degradation detector watches the
   time of each benchmark on master.
3. Call `report(result)`. It prints `PYPERF` lines: the time quantiles, the allocations, the AST loads of the first
   attempt, and each counter with its median, its range and the value of the first attempt.

The system property `pyperf.out` names a file that also gets the `PYPERF` lines. The system property
`pyperf.astload.stacks=true` prints the stack of the first three AST loads of each file.

## Read the result

- `cold()` drops the PSI caches and the type contexts. The PSI, the stubs, the control flow and the soft caches stay.
  So the counts of one scenario change between attempts. For a cold highlighting of `pandas_examples.py`, the range of
  `type.getType.evaluations` is about 20 % of the median.
- Compare medians, and read the `range=` value before you claim a difference.
- For the complexity of an algorithm, measure the input at several sizes (n, 2n, 4n) and compare the growth of a
  counter. Do not compare single times.

## Add a temporary counter

1. Add an entry to `PyCodeInsightCounters.Counter` with a stable dotted id, for example `type.foo.calls`.
2. At the hook, call `PyCodeInsightCounters.inc(Counter.FOO_CALLS)` or `add(counter, value)`. When the counting is
   off, a call costs one volatile read. Put other work at the hook behind `PyCodeInsightCounters.isEnabled`.
3. Keep a counter in the merge request only when a test asserts on it. Remove the others before the merge.
