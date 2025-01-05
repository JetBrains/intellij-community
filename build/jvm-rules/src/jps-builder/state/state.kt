package org.jetbrains.bazel.jvm.jps.state

internal enum class TargetStateProperty {
  AverageBuildTime,
  LastSuccessfulRebuildDuration,
  ;

  companion object {
    val VERSION = versionDigest<TargetStateProperty>()
  }
}

private fun emptyContainer(): LongArray {
  val list = LongArray(TargetStateProperty.entries.size + 1) { -1 }
  list[list.lastIndex] = TargetStateProperty.VERSION
  return list
}

@JvmInline
internal value class TargetStateContainer(private val list: LongArray = emptyContainer()) {
  fun get(kind: TargetStateProperty): Long = list[kind.ordinal]

  fun set(kind: TargetStateProperty, value: Long) {
    list[kind.ordinal] = value
  }

  fun asArray(): LongArray = list.copyOf()

  val isCorrect: Boolean
    get() = list.size == (TargetStateProperty.entries.size + 1) && list.last() == TargetStateProperty.VERSION
}