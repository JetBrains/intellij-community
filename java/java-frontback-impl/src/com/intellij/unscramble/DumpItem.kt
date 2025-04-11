// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import com.intellij.threadDumpParser.ThreadOperation
import com.intellij.threadDumpParser.ThreadState
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.util.*
import javax.swing.Icon

@ApiStatus.Internal
interface DumpItem {
  val name: @NlsSafe String

  val stateDesc: @NlsSafe String

  val stackTrace: @NlsSafe String

  val interestLevel: Int

  val icon: Icon

  val attributes: SimpleTextAttributes

  val isDeadLocked: Boolean

  /**
   * When having a list of [DumpItem]s, it is expected that contents of this set are instances from the list.
   * @see toDumpItems
   */
  val awaitingDumpItems: Set<DumpItem>

  companion object {
    @JvmField
    val SLEEPING_ATTRIBUTES: SimpleTextAttributes = SimpleTextAttributes.GRAY_ATTRIBUTES

    @JvmField
    val RUNNING_ATTRIBUTES: SimpleTextAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES

    @JvmField
    val UNINTERESTING_ATTRIBUTES: SimpleTextAttributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.GRAY.brighter())

    @JvmField
    val BY_NAME: Comparator<DumpItem> = Comparator<DumpItem> { o1, o2 ->
      o1.name.compareTo(o2.name, true)
    }

    @JvmField
    val BY_INTEREST: Comparator<DumpItem> = Comparator<DumpItem> { o1, o2 ->
      o2.interestLevel - o1.interestLevel
    }
  }
}

@ApiStatus.Internal
interface MergeableDumpItem : DumpItem {
  val mergeableToken: MergeableToken
}

@ApiStatus.Internal
object IconsCache {
  private val virtualIcons: MutableMap<Icon, IconWithVirtualOverlay> = mutableMapOf()
  private val daemonIcons: MutableMap<Icon, IconWithDaemonOverlay> = mutableMapOf()

  fun getIconWithVirtualOverlay(baseIcon: Icon): Icon =
    virtualIcons.computeIfAbsent(baseIcon) { IconWithVirtualOverlay(it) }

  fun getIconWithDaemonOverlay(baseIcon: Icon): Icon =
    daemonIcons.computeIfAbsent(baseIcon) { IconWithDaemonOverlay(it) }
}


@ApiStatus.Internal
interface MergeableToken {
  override fun equals(other: Any?): Boolean
  override fun hashCode(): Int

  val item: DumpItem
}

@ApiStatus.Internal
class CompoundDumpItem<T : DumpItem>(
  val originalItem: T,
  val counter: Int,
) : DumpItem by originalItem {

  override val name: String = originalItem.name + (if (counter == 1) "" else " [and ${counter - 1} similar]")

  companion object {
    @JvmStatic
    fun mergeThreadDumpItems(originalItems: List<MergeableDumpItem>): List<DumpItem> =
      originalItems
        .groupingBy { it.mergeableToken }
        .eachCount()
        .map { (token, count) ->
          val item = token.item
          if (count > 1) CompoundDumpItem(item, count) else item
        }
  }
}

@ApiStatus.Internal
fun List<ThreadState>.toDumpItems(): List<MergeableDumpItem> {
  val statesToItems = associateWith(::JavaThreadDumpItem)

  for ((threadState, dumpItem) in statesToItems) {
    val awaitingItems = threadState.awaitingThreads.mapNotNull { statesToItems[it] }.toSet()
    dumpItem.setAwaitingItems(awaitingItems)
  }

  return statesToItems.values.toList()
}

private class JavaThreadDumpItem(private val threadState: ThreadState) : MergeableDumpItem {
  override val name: String = threadState.name

  override val stateDesc: String
    get() {
      val trimmedState = (threadState.threadStateDetail ?: threadState.state).let {
        if (it.length > 30) {
          it.take(30) + "..."
        }
        else it
      }
      val extraState = threadState.extraState
      return buildString {
        if (trimmedState.isNotEmpty() && trimmedState != "unknown" && trimmedState != "undefined") {
          append(" ($trimmedState)")
        }
        if (extraState != null) {
          append(" [$extraState]")
        }
      }
    }

  override val stackTrace: String = threadState.stackTrace ?: ""

  private val isServiceThread: Boolean =
    name.startsWith("Coroutines Debugger Cleaner") ||
    name.startsWith("IntelliJ Suspend Helper")

  override val interestLevel: Int = when {
    threadState.isEmptyStackTrace -> -10
    this.isServiceThread || threadState.isKnownJDKThread -> -5
    threadState.isSleeping -> -2
    threadState.operation == ThreadOperation.SOCKET -> -1
    else -> stackTrace.count { it == '\n' }
  }

  override val icon: Icon
    get() {
      val baseIcon = when {
        threadState.isSleeping -> AllIcons.Debugger.MuteBreakpoints
        // Should be checked before checking isWaiting().
        threadState.operation == ThreadOperation.CARRYING_VTHREAD -> AllIcons.Debugger.ThreadGroup
        threadState.isWaiting -> AllIcons.Debugger.ThreadFrozen
        threadState.operation == ThreadOperation.SOCKET -> AllIcons.Debugger.ThreadStates.Socket
        threadState.operation == ThreadOperation.IO -> AllIcons.Actions.MenuSaveall
        threadState.isEDT ->
          if (threadState.isIdle) {
            AllIcons.Debugger.ThreadStates.Idle
          }
          else {
            AllIcons.Actions.ProfileCPU
          }
        else -> AllIcons.Actions.Resume
      }
      return when {
        threadState.isVirtual -> IconsCache.getIconWithVirtualOverlay(baseIcon)
        threadState.isDaemon -> IconsCache.getIconWithDaemonOverlay(baseIcon)
        else -> baseIcon
      }
    }

  override val attributes: SimpleTextAttributes = when {
    threadState.isSleeping -> DumpItem.SLEEPING_ATTRIBUTES
    threadState.isEmptyStackTrace || this.isServiceThread || threadState.isKnownJDKThread() -> DumpItem.UNINTERESTING_ATTRIBUTES
    threadState.isEDT() -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
    else -> SimpleTextAttributes.REGULAR_ATTRIBUTES
  }

  override val isDeadLocked: Boolean
    get() = threadState.isDeadlocked

  private var internalAwaitingItems = emptySet<DumpItem>()

  override val awaitingDumpItems: Set<DumpItem>
    get() = internalAwaitingItems

  fun setAwaitingItems(awaitingItems: Set<DumpItem>) {
    internalAwaitingItems = awaitingItems
  }

  override val mergeableToken: MergeableToken get() = JavaMergeableToken()

  private inner class JavaMergeableToken : MergeableToken {
    private val comparableStackTrace: String =
      stackTrace.substringAfter("\n").replace("<0x\\d+>\\s".toRegex(), "<merged>")

    override val item: JavaThreadDumpItem get() = this@JavaThreadDumpItem

    override fun equals(other: Any?): Boolean {
      if (other !is JavaMergeableToken) return false
      val otherThreadState = other.item.threadState
      if (threadState.isEDT()) return false
      if (threadState.state != otherThreadState.state) return false
      if (threadState.isEmptyStackTrace != otherThreadState.isEmptyStackTrace) return false
      if (threadState.isDaemon != otherThreadState.isDaemon) return false
      if (threadState.javaThreadState != otherThreadState.javaThreadState) return false
      if (threadState.threadStateDetail != otherThreadState.threadStateDetail) return false
      if (threadState.extraState != otherThreadState.extraState) return false
      if (threadState.awaitingThreads != otherThreadState.awaitingThreads) return false
      if (threadState.deadlockedThreads != otherThreadState.deadlockedThreads) return false
      if (this.comparableStackTrace != other.comparableStackTrace) return false
      return true
    }

    override fun hashCode(): Int {
      return Objects.hash(
        threadState.state,
        threadState.isEmptyStackTrace,
        threadState.isDaemon,
        threadState.javaThreadState,
        threadState.threadStateDetail,
        threadState.extraState,
        threadState.awaitingThreads,
        threadState.deadlockedThreads,
        comparableStackTrace
      )
    }
  }
}

