// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.state

import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import org.h2.mvstore.type.LongDataType
import org.jetbrains.bazel.jvm.jps.impl.BazelBuildTargetStateManager
import org.jetbrains.jps.incremental.storage.StorageManager
import org.jetbrains.jps.incremental.storage.dataTypes.LongListKeyDataType

private const val targetStateMapName = "target-state"

private const val DIGEST_LIST_KEY = 1L
private const val DURATION_KEY = 2L

private val targetStateMapBuilder = run {
  val mapBuilder = MVMap.Builder<Long, LongArray>()
  mapBuilder.setKeyType(LongDataType.INSTANCE)
  mapBuilder.setValueType(LongListKeyDataType)
  mapBuilder
}

// returns a reason to force rebuild
internal fun checkConfiguration(
  store: MVStore,
  targetDigests: TargetConfigurationDigestContainer,
): String? {
  val map = try {
    store.openMap(targetStateMapName, targetStateMapBuilder)
  }
  catch (e: Throwable) {
    return "Cannot open map $targetStateMapName: ${e.stackTraceToString()}"
  }

  val storedDigest = map.get(DIGEST_LIST_KEY)?.let { TargetConfigurationDigestContainer(it) }
  when {
    storedDigest == null -> {
      return "Configuration digest not found"
    }
    storedDigest.rawSize != targetDigests.rawSize -> {
      return "Configuration digest size mismatch: expected ${targetDigests.rawSize}, got ${storedDigest.rawSize}"
    }
    storedDigest.version != TargetConfigurationDigestProperty.VERSION -> {
      return "Configuration digest format version mismatch: expected ${TargetConfigurationDigestProperty.VERSION}, got ${storedDigest.version}"
    }
  }

  for (kind in TargetConfigurationDigestProperty.entries) {
    val storedHash = storedDigest.get(kind)
    val hash = targetDigests.get(kind)
    if (hash != storedHash) {
      return "Configuration digest mismatch (${kind.description}): expected $hash, got $storedHash"
    }
  }
  return null
}

internal fun saveTargetState(
  targetDigests: TargetConfigurationDigestContainer,
  manager: BazelBuildTargetStateManager,
  storageManager: StorageManager
) {
  val map = storageManager.openMap(targetStateMapName, targetStateMapBuilder).map
  map.operate(DIGEST_LIST_KEY, targetDigests.asArray(), PutIfChanged)
  map.operate(DURATION_KEY, manager.state.asArray(), PutIfChanged)
}

private object PutIfChanged : MVMap.DecisionMaker<LongArray>() {
  override fun decide(existingValue: LongArray?, providedValue: LongArray): MVMap.Decision {
    return if (providedValue.contentEquals(existingValue)) MVMap.Decision.ABORT else MVMap.Decision.PUT
  }
}

internal fun loadTargetState(storageManager: StorageManager): TargetStateContainer {
  val map = storageManager.openMap(targetStateMapName, targetStateMapBuilder).map
  // copyOf - do not mutate data in store directly
  return map.get(DURATION_KEY)?.let { TargetStateContainer(it.copyOf()) }?.takeIf { it.isCorrect } ?: TargetStateContainer()
}