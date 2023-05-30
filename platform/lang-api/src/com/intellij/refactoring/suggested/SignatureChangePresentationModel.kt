// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.suggested

import com.intellij.refactoring.suggested.SignatureChangePresentationModel.Effect
import com.intellij.refactoring.suggested.SignatureChangePresentationModel.TextFragment

class SignatureChangePresentationModel(
  val oldSignature: List<TextFragment>,
  val newSignature: List<TextFragment>
) {
  sealed class TextFragment(val connectionId: Any?, val effect: Effect) {
    abstract fun forSelfAndDescendants(action: (TextFragment) -> Unit)
    abstract fun anySelfOrDescendants(predicate: (TextFragment) -> Boolean): Boolean

    abstract fun withConnectionId(connectionId: Any?): TextFragment
    abstract fun withEffect(effect: Effect): TextFragment

    class Leaf
    @JvmOverloads constructor(
      val text: String,
      effect: Effect = Effect.None,
      connectionId: Any? = null
    ) : TextFragment(connectionId, effect) {

      override fun forSelfAndDescendants(action: (TextFragment) -> Unit) {
        action(this)
      }

      override fun anySelfOrDescendants(predicate: (TextFragment) -> Boolean): Boolean = predicate(this)

      override fun withConnectionId(connectionId: Any?): Leaf = Leaf(text, effect, connectionId)

      override fun withEffect(effect: Effect): Leaf = Leaf(text, effect, connectionId)
    }

    class Group(
      val children: List<TextFragment>,
      effect: Effect,
      connectionId: Any?
    ) : TextFragment(connectionId, effect) {

      override fun forSelfAndDescendants(action: (TextFragment) -> Unit) {
        action(this)
        children.forAllFragments(action)
      }

      override fun anySelfOrDescendants(predicate: (TextFragment) -> Boolean): Boolean = predicate(this) || children.any { it.anySelfOrDescendants(predicate) }

      override fun withConnectionId(connectionId: Any?): Group =
        Group(children, effect, connectionId)

      override fun withEffect(effect: Effect): Group =
        Group(children, effect, connectionId)
    }

    class LineBreak(val spaceInHorizontalMode: String, val indentAfter: Boolean) : TextFragment(null, Effect.None) {
      override fun forSelfAndDescendants(action: (TextFragment) -> Unit) {
        action(this)
      }

      override fun anySelfOrDescendants(predicate: (TextFragment) -> Boolean): Boolean = predicate(this)

      override fun withConnectionId(connectionId: Any?): TextFragment {
        throw UnsupportedOperationException()
      }

      override fun withEffect(effect: Effect): TextFragment {
        throw UnsupportedOperationException()
      }
    }
  }

  enum class Effect {
    None, Modified, Added, Removed, Moved
  }
}

fun List<TextFragment>.forAllFragments(action: (TextFragment) -> Unit) {
  forEach { it.forSelfAndDescendants(action) }
}

fun SignatureChangePresentationModel.checkCorrectness() {
  fun checkConnectionsAtTopLevelOnly(fragments: List<TextFragment>) {
    for (fragment in fragments) {
      fragment.forSelfAndDescendants {
        if (it != fragment) {
          check(it.connectionId == null) { "Connections supported only for top-level fragments" }
        }
      }
    }
  }

  checkConnectionsAtTopLevelOnly(oldSignature)
  checkConnectionsAtTopLevelOnly(newSignature)

  fun connectionIdSet(fragments: List<TextFragment>): Set<Any> {
    val set = mutableSetOf<Any>()
    for (fragment in fragments) {
      val id = fragment.connectionId ?: continue
      val added = set.add(id)
      check(added) { "Duplicate connectionId: $id" }
    }
    return set
  }

  val idSet1 = connectionIdSet(oldSignature)
  val idSet2 = connectionIdSet(newSignature)
  check(idSet1 == idSet2) { "Mismatching set of connectionId's for oldSignature and newSignature" }

  fun checkEffectWithConnection(fragment: TextFragment) {
    if (fragment.connectionId != null) {
      check(fragment.effect == Effect.None || fragment.effect == Effect.Modified || fragment.effect == Effect.Moved) {
        "Incorrect effect for fragment with connection: ${fragment.effect}"
      }
    }
  }
  oldSignature.forEach(::checkEffectWithConnection)
  newSignature.forEach(::checkEffectWithConnection)
}

fun SignatureChangePresentationModel.improvePresentation(): SignatureChangePresentationModel {
  var model = this

  model.checkCorrectness()

  fun checkNoMovedEffect(it: TextFragment) {
    require(it.effect != Effect.Moved) { "Don't use Effect.Moved, it's generated automatically by improvePresentation()" }
  }

  model.oldSignature.forAllFragments(::checkNoMovedEffect)
  model.newSignature.forAllFragments(::checkNoMovedEffect)

  model = model.dropRedundantConnections()
  model = model.addMovedEffectForAllConnections()
  model = model.dropObviousSingleConnection()

  model.checkCorrectness()

  return model
}

private fun SignatureChangePresentationModel.dropRedundantConnections(): SignatureChangePresentationModel {
  val connectionIdsWithModifications = mutableSetOf<Any>().apply {
    oldSignature
      .filter { it.connectionId != null && it.hasModifications() }
      .mapTo(this) { it.connectionId!! }
    newSignature
      .filter { it.connectionId != null && it.hasModifications() }
      .mapTo(this) { it.connectionId!! }
  }

  // Let's find the longest subsequence of elements in the new signature connected to elements in the old signature
  // with increasing indices. We will display elements in this subsequence as not moved.

  val idToOldIndex = mutableMapOf<Any, Int>()
  for ((index, fragment) in oldSignature.withIndex()) {
    if (fragment.connectionId != null) {
      idToOldIndex[fragment.connectionId] = index
    }
  }

  val newConnectionIds = newSignature.mapNotNull { it.connectionId }

  // we use value function to prefer fragments without modifications to be included in the subsequence
  // (it's better to show element as moved if it has other modifications inside)
  val normalWeight = 1.0
  val modifiedWeight = 1 - 1.0 / (newConnectionIds.size + 1) // less than 1.0 but not enough to prefer less elements in the subsequence

  fun weightFunction(oldIndex: Int): Double {
    val connectionId = oldSignature[oldIndex].connectionId!!
    return if (connectionId in connectionIdsWithModifications) modifiedWeight else normalWeight
  }

  val dropConnectionIds = findMaximumWeightIncreasingSubsequence(newConnectionIds.map { idToOldIndex[it]!! }, ::weightFunction)
    .map { newConnectionIds[it] }
    .toSet()

  fun process(fragment: TextFragment): TextFragment {
    return if (fragment.connectionId != null && fragment.connectionId in dropConnectionIds)
      fragment.withConnectionId(null)
    else
      fragment
  }

  return SignatureChangePresentationModel(oldSignature.map { process(it) }, newSignature.map { process(it) })
}

// we don't show single parameter move if there are no other fragments marked as modified
private fun SignatureChangePresentationModel.dropObviousSingleConnection(): SignatureChangePresentationModel {
  val singleConnectionId = oldSignature.mapNotNull { it.connectionId }.singleOrNull() ?: return this

  fun List<TextFragment>.hasModificationsOutsideSingleConnection() =
    filter { it.connectionId != singleConnectionId }.hasModifications()

  if (oldSignature.hasModificationsOutsideSingleConnection() || newSignature.hasModificationsOutsideSingleConnection()) return this

  fun process(fragment: TextFragment) =
    if (fragment.connectionId != null) fragment.withConnectionId(null) else fragment

  return SignatureChangePresentationModel(oldSignature.map { process(it) }, newSignature.map { process(it) })
}

private fun SignatureChangePresentationModel.addMovedEffectForAllConnections(): SignatureChangePresentationModel {
  return SignatureChangePresentationModel(
    oldSignature.map { it.addMovedEffectIfConnected() },
    newSignature.map { it.addMovedEffectIfConnected() }
  )
}

private fun TextFragment.addMovedEffectIfConnected(): TextFragment {
  if (connectionId == null) return this
  if (effect != Effect.None) return this
  return withEffect(Effect.Moved)
}

private fun TextFragment.hasModifications() = anySelfOrDescendants { it.effect != Effect.None }

private fun List<TextFragment>.hasModifications(): Boolean {
  return any { it.hasModifications() }
}

