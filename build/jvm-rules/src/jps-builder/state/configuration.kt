package org.jetbrains.bazel.jvm.jps.state

internal enum class TargetConfigurationDigestProperty(@JvmField val description: String) {
  COMPILER("kotlinc/javac configuration"),
  DEPENDENCY_PATH_LIST("dependency path list"),
  DEPENDENCY_DIGEST_LIST("dependency digest list");
}

private fun emptyContainer(): LongArray {
  return LongArray(TargetConfigurationDigestProperty.entries.size)
}

@JvmInline
internal value class TargetConfigurationDigestContainer(
  private val list: LongArray = emptyContainer(),
) {
  fun get(kind: TargetConfigurationDigestProperty): Long = list[kind.ordinal]

  fun set(kind: TargetConfigurationDigestProperty, hash: Long) {
    list[kind.ordinal] = hash
  }
}