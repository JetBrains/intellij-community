package org.jetbrains.bazel.jvm.jps.state

internal enum class TargetConfigurationDigestProperty(@JvmField val description: String) {
  COMPILER("kotlinc/javac configuration"),
  DEPENDENCY_PATH_LIST("dependency path list"),
  DEPENDENCY_DIGEST_LIST("dependency digest list");

  companion object {
    val VERSION = versionDigest<TargetConfigurationDigestProperty>()
  }
}

private fun emptyContainer(): LongArray {
  val list = LongArray(TargetConfigurationDigestProperty.entries.size + 1)
  list[list.lastIndex] = TargetConfigurationDigestProperty.VERSION
  return list
}

@JvmInline
internal value class TargetConfigurationDigestContainer(
  private val list: LongArray = emptyContainer(),
) {
  fun get(kind: TargetConfigurationDigestProperty): Long = list[kind.ordinal]

  fun set(kind: TargetConfigurationDigestProperty, hash: Long) {
    list[kind.ordinal] = hash
  }

  fun asArray(): LongArray = list.copyOf()

  val version: Long
    get() = list.last()

  val rawSize: Int
    get() = list.size
}