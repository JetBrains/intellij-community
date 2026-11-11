# Documenting Constants and Magic Numbers

**Summary:** always add a comment to a numeric constant, buffer size, timeout, or limit to explain why it has that specific value.

This rule applies to everyone who writes or edits Eel or IJent code: human developers and AI agents.

## Why Constants Need Explanation

A bare number in code (such as `64 * 1024`, `5`, or `1000`) does not tell the reader how the author chose it. When a developer later debugs a performance problem or a timeout, they cannot tell if changing the number is safe.

Every constant falls into one of two categories:

1. **Arbitrary values:** the author picked a round number at random or by intuition. Anyone can change or tune the value freely.
2. **Researched or logical values:** the author chose the value after benchmarks, experiments, or analysis of a protocol or OS limit. Do not change the value without new benchmarks or verification of the constraint.

Without a comment, a reader treats a random guess as a fixed rule, or breaks a tuned value by accident.

## Category 1: Arbitrary Values That You Can Change Freely

Often, code needs a buffer size, a channel capacity, or a retry count before any benchmark exists. Picking a reasonable number at random is fine. Write a comment that states you chose the value at random and anyone can change it freely. If you had a rough trade-off in mind, mention it briefly.

```kotlin
// We chose 64 KiB at random to balance I/O call overhead against the frequency of progress
// and cancellation checks. No benchmark backs this value, so you can change it freely.
private const val BUFFER_SIZE = 64 * 1024
```

Another example from `com.intellij.platform.eel.provider.utils.EelPathTransfer`:

```kotlin
// We chose the buffer capacity of 5 at random to keep several chunks ready for the writer.
// You can change this value freely.
}.flowOn(Dispatchers.IO).buffer(5)
```

## Category 2: Researched or Logical Values That You Must Not Change

Some constants come from benchmarks, empirical research, protocol specifications, or logical invariants. Write a comment that explains the evidence or the logical requirement behind the value. State clearly that future authors must not change the value without re-evaluating that reason.

### Example: Benchmarked Value

From `com.intellij.platform.eel.provider.utils.EelPathTransfer`:

```kotlin
// This 256 KiB buffer size gave the best overall throughput in cross-environment streaming benchmarks.
// Do not change it without re-running the transfer benchmarks.
val buffer = ByteBuffer.allocate(256 * 1024)
```

### Example: Logical or Protocol Constraint

```kotlin
// The packet header uses 1 byte, so the payload must not exceed 65534 bytes to fit into
// an unsigned 16-bit frame length. Do not change this value.
private const val MAX_PAYLOAD_SIZE = 65535 - 1
```
