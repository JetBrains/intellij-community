---
name: ide-diagnostics-mcp
description: Inspect the live runtime state of a currently running IntelliJ IDE through the MCP get_ide_diagnostics tool. Use only for IDE process diagnostics such as performance triage, UI freezes, high CPU, blocked threads, coroutine dumps, or thread dumps when the user wants current evidence from the running IDE, not for general debugging, generic issue diagnosis, build/test failures, logs, or MCP server self-diagnostics.
---

# IDE Diagnostics MCP

Use the `get_ide_diagnostics` MCP tool to answer "what is this IDE doing right now?" from inside a running IntelliJ process.

This skill is for live IDE process evidence only. If the request is to diagnose an arbitrary issue, inspect repository code, analyze build/test output, or debug the MCP server itself, use the relevant debugging, testing, or code-investigation workflow instead.

## Availability

The diagnostics toolset is hidden unless the IDE was started with:

```bash
-Didea.diagnostics.mcp.enabled=true
```

If `get_ide_diagnostics` is not available, tell the user the IDE must be restarted with that property. Do not confuse it with MCP server self-diagnostics; the property enables IDE process diagnostics exposed through MCP.

## Capture Workflow

Start with a lightweight CPU sample without the raw dump:

```json
{
  "sampleMillis": 1000,
  "topThreadCount": 25,
  "includeRawDump": false
}
```

For freezes, blocked threads, or coroutine investigation, request a bounded raw dump:

```json
{
  "sampleMillis": 1000,
  "topThreadCount": 50,
  "includeRawDump": true,
  "maxDumpChars": 200000,
  "stripCoroutineDump": true
}
```

For action-specific performance questions, take one sample before or during the action and another while the symptom is visible. Compare `topCpuThreads`, `blockedCountDelta`, `waitedCountDelta`, and `threads.stateCounts` between samples.

## Interpretation

- `process.processCpuLoad` and `process.systemCpuLoad` are point-in-time process/system CPU load values; `-1` means the JVM cannot provide the metric.
- `topCpuThreads[].cpuDeltaNanos` ranks threads by CPU consumed during `sampleMillis`; `-1` means thread CPU time is unavailable.
- `topCpuThreads[].state` is the JVM `Thread.State`; `RUNNABLE` includes Java execution and native calls, including native I/O.
- `topCpuThreads[].isInNative`, `nativeFrame`, and `nativeOperationHint` help identify native calls, but they are not OS wait-channel data.
- `topCpuThreads[].stackTrace` is a short stack snapshot for attribution, not a full profiler call tree.
- `threads.stateCounts` is useful for spotting many `BLOCKED`, `WAITING`, or `TIMED_WAITING` threads.
- `rawDump` is the IntelliJ thread dump text and may include coroutine dump content when coroutine dumping is enabled in the running IDE.
- `rawDumpTruncated=true` means repeat with a larger `maxDumpChars` or ask for a narrower next step before drawing conclusions from missing tail content.
- `coroutineDumpEnabled=false` means the tool cannot provide coroutine dump details beyond ordinary thread stacks.

## Escalation

Use this MCP tool for low-overhead triage and evidence gathering. For root-cause CPU attribution, allocation pressure, lock profiling, native wait-state attribution, or long recordings, ask for JFR, async-profiler, or the IDE's standard performance capture workflow.
