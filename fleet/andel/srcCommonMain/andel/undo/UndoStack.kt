package andel.undo

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

data class UndoStack<C>(
  val executedStack: PersistentList<C> = persistentListOf(),
  val revertedStack: PersistentList<C> = persistentListOf(),
) {
  sealed interface Action<T> {
    data class Replace<T>(val merged: T) : Action<T>
    data class Append<T>(val new: T) : Action<T>
    class Nothing<T> : Action<T>
  }

  fun interface Amend<T> {
    /**
     * Two consequent records of the same command could be merged.
     */
    fun amend(prev: T?, next: T): Action<T>
  }

  fun pushExecuted(command: C, amend: Amend<C>? = null): UndoStack<C> {
    return pushExecuted(command, amend, clearRevertedOnChange = true)
  }

  fun pushExecutedFromReverted(command: C): UndoStack<C> {
    return pushExecuted(command, amend = null, clearRevertedOnChange = false)
  }

  private fun pushExecuted(command: C, amend: Amend<C>?, clearRevertedOnChange: Boolean): UndoStack<C> {
    val action = amend?.amend(executedStack.lastOrNull(), command) ?: Action.Append(command)
    val newExecutedStack = when (action) {
      is Action.Replace -> {
        if (executedStack.isEmpty()) executedStack.adding(action.merged)
        else executedStack.replacingAt(executedStack.lastIndex, action.merged)
      }
      is Action.Append -> executedStack.adding(action.new)
      is Action.Nothing -> executedStack
    }
    return copy(
      executedStack = newExecutedStack,
      revertedStack = if (clearRevertedOnChange && action is Action.Append) persistentListOf() else revertedStack,
    )
  }

  fun peekExecuted(): C? {
    return executedStack.lastOrNull()
  }

  fun removeNextExecuted(): Pair<UndoStack<C>, C>? {
    val next = peekExecuted() ?: return null
    return copy(executedStack = executedStack.removingAt(executedStack.lastIndex)) to next
  }

  fun pushReverted(command: C): UndoStack<C> {
    return copy(revertedStack = revertedStack.adding(command))
  }

  fun clearReverted(): UndoStack<C> {
    return copy(revertedStack = persistentListOf())
  }

  fun peekReverted(): C? {
    return revertedStack.lastOrNull()
  }

  fun removeNextReverted(): Pair<UndoStack<C>, C>? {
    val next = peekReverted() ?: return null
    return copy(revertedStack = revertedStack.removingAt(revertedStack.lastIndex)) to next
  }
}

fun <T, U> UndoStack.Action<T>.map(f: (T) -> U): UndoStack.Action<U> {
  return when (val c = this) {
    is UndoStack.Action.Append<T> -> UndoStack.Action.Append(f(c.new))
    is UndoStack.Action.Nothing<T> -> UndoStack.Action.Nothing()
    is UndoStack.Action.Replace<T> -> UndoStack.Action.Replace(f(c.merged))
  }
}