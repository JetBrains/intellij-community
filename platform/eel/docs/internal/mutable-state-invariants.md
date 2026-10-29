# Enforcing Mutable State Invariants with the Type System

**Summary:** when a class has complex mutable state, encode its invariants in the type system with algebraic data types (`sealed interface` or `sealed class`). Do not rely on separate mutable fields that allow impossible combinations.

This rule applies to everyone who writes or edits Eel or IJent code: human developers and AI agents.

## When to Refactor

Refactor a class or state holder when both of the following conditions apply:

1. The class has three or more mutable fields or mutable collections. Pay special attention when some of these fields are nullable (`T?`) or `Boolean`.
2. The fields allow a theoretical combination of values that can never happen in reality.

### Example of Unconstrained Mutable Fields

```kotlin
// Incorrect: separate mutable fields allow impossible combinations
class State {
  var refCount: Int = 0
  var closed: Boolean = false
  var eelApi: EelApi? = null
  var fileSystem: FileSystem? = null
  var closeFileSystemOnRelease: Boolean = false
}
```

In this example, the type system allows `eelApi == null` while `fileSystem != null`, or `closed == true` while `eelApi != null`. In reality, these combinations are invalid.

## Why Separate Mutable Fields Cause Problems

- **Higher cognitive load:** a reader must read every mutation site to learn which combinations of field values can happen and which cannot.
- **Partial updates on failure:** if an exception occurs between two field assignments, the object remains in an invalid intermediate state.
- **Documentation drift:** even when comments document the invariant, a later change can easily break the invariant or leave the comments outdated.

## How to Refactor: Finite State Machine with Sealed Types

Represent the mutable state as a finite state machine (FSM) with a `sealed interface` or `sealed class`. Each subtype holds only the data that exists in that state. Store the current state in a single reference, such as an `AtomicReference` or a field guarded by a mutex.

Check the state with an [exhaustive `when`](../api/exhaustive-when.md). When a state changes or a new state is added, the compiler reports every transition site that needs an update.

### Example: `EelPipeImpl`

`com.intellij.platform.eel.provider.utils.EelPipeImpl` models the lifecycle of a byte pipe with a sealed hierarchy instead of separate `buffer`, `isTransferring`, `isClosing`, and `error` fields:

```kotlin
private sealed interface State {
  object Idle : State

  sealed interface TransferState : State {
    val bufferToSend: ByteBuffer
  }

  class ReadyToTransfer(override val bufferToSend: ByteBuffer) : TransferState {
    val initialPos: Int = bufferToSend.position()
  }

  class TransferringNow(override val bufferToSend: ByteBuffer) : TransferState

  class LastTransfer(val currentState: TransferState, val nextState: Closed) : State

  class Closed(val error: Throwable?) : State
}
```

This hierarchy enforces the invariants at compile time:

- `Idle` has no `ByteBuffer` and no error.
- Only `TransferState` subtypes (`ReadyToTransfer` and `TransferringNow`) hold a non-null `bufferToSend`.
- `LastTransfer` can wrap only an active `TransferState` and a target `Closed` state.
- Only `Closed` can hold a termination `error`.

### Example: `EelMachineConnectionRegistry.MachineLeaseSlot`

For the lease state shown earlier, `EelMachineConnectionRegistry` uses a sealed hierarchy that eliminates impossible combinations of `eelApi`, `fileSystem`, and `closeFileSystemOnRelease`:

```kotlin
// Correct: invalid field combinations are unrepresentable
sealed interface State {
  val refCount: Int
  fun withRefCount(refCount: Int): State

  class Unconnected(override val refCount: Int) : State {
    override fun withRefCount(refCount: Int): Unconnected = Unconnected(refCount)
  }

  class Connected(
    override val refCount: Int,
    val eelApi: EelApi,
    val fileSystem: FileSystem,
  ) : State {
    override fun withRefCount(refCount: Int): Connected = Connected(refCount, eelApi, fileSystem)
  }
}
```

In this representation:

- Every `State` instance is immutable, so the slot can store its state in a single `AtomicReference<State>` without a mutex.
- `Unconnected` holds only `refCount` (for example, `0` when idle or `> 0` when an open project holds a lease before an `EelApi` session is initialized).
- `Connected` guarantees both a non-null `eelApi` and a non-null `fileSystem` (since local machines never allocate a `MachineLeaseSlot`), eliminating the need for a separate `closeFileSystemOnRelease: Boolean` flag.
