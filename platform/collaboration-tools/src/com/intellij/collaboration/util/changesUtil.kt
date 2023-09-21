// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.HashingStrategy
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
sealed class ChangesSelection(
  val changes: List<Change>,
  val selectedIdx: Int
) {

  abstract fun copyWithSelection(change: Change): ChangesSelection

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ChangesSelection

    if (!changes.isEqual(other.changes)) return false
    if (selectedIdx != other.selectedIdx) return false

    return true
  }

  override fun hashCode(): Int {
    var result = changes.calcHashCode()
    result = 31 * result + selectedIdx
    return result
  }

  /**
   * Single change selected from [changes]
   */
  class Precise(changes: List<Change>, selectedIdx: Int = 0, val location: DiffLineLocation? = null)
    : ChangesSelection(changes, selectedIdx) {

    constructor(changes: List<Change>, change: Change, location: DiffLineLocation? = null)
      : this(changes, changes.indexOfFirst { it === change }, location)

    override fun copyWithSelection(change: Change): ChangesSelection = Precise(changes, changes.indexOfFirst { it === change })

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false
      if (!super.equals(other)) return false

      other as Precise

      return location == other.location
    }

    override fun hashCode(): Int {
      var result = super.hashCode()
      result = 31 * result + (location?.hashCode() ?: 0)
      return result
    }

    override fun toString(): String = "ChangesSelection.Precise(change=$selectedChange, location=$location)"
  }

  /**
   * Changes selected by a certain group (like directory)
   */
  class Fuzzy(changes: List<Change>, selectedIdx: Int = 0) : ChangesSelection(changes, selectedIdx) {
    override fun copyWithSelection(change: Change): ChangesSelection = Fuzzy(changes, changes.indexOfFirst { it === change })

    override fun toString(): String = "ChangesSelection.Fuzzy(change=$selectedChange)"
  }
}

val ChangesSelection.selectedChange: Change?
  get() = selectedIdx.let { changes.getOrNull(it) }

@ApiStatus.Experimental
fun ChangesSelection?.equalChanges(other: Any?): Boolean {
  if (this == null && other != null) return false
  if (this != null && other == null) return false
  if (other === this) return true
  if (this == null || other == null) return false // for null safety

  other as ChangesSelection

  if (!changes.isEqual(other.changes)) return false
  if (selectedIdx != other.selectedIdx) return false
  return true
}

@ApiStatus.Experimental
@ApiStatus.Internal
fun Collection<Change>?.isEqual(other: Collection<Change>?, ordered: Boolean = false): Boolean =
  equalsVia(other, CODE_REVIEW_CHANGE_HASHING_STRATEGY, ordered)

@ApiStatus.Experimental
@ApiStatus.Internal
fun <E> Collection<E>?.equalsVia(other: Collection<E>?, strategy: HashingStrategy<E>, ordered: Boolean = false): Boolean {
  if (this == null && other != null) return false
  if (this != null && other == null) return false
  if (other === this) return true
  if (this == null || other == null) return false // for null safety
  if (size != other.size) return false

  if (ordered) {
    val i1 = iterator()
    val i2 = other.iterator()

    while (i1.hasNext() && i2.hasNext()) {
      val e1 = i1.next()
      val e2 = i2.next()
      if (!strategy.equals(e1, e2)) return false
    }
    return !(i1.hasNext() || i2.hasNext())
  }
  else {
    val thisSet = CollectionFactory.createCustomHashingStrategySet(strategy).apply {
      addAll(this)
    }
    val otherSet = CollectionFactory.createCustomHashingStrategySet(strategy).apply {
      addAll(other)
    }
    return thisSet == otherSet
  }
}

@ApiStatus.Experimental
@ApiStatus.Internal
fun Change.isEqual(other: Change?): Boolean = CODE_REVIEW_CHANGE_HASHING_STRATEGY.equals(this, other)

@ApiStatus.Experimental
@ApiStatus.Internal
//java.util.List.hashCode
fun List<Change>.calcHashCode(): Int {
  var hashCode = 1
  for (change in this) hashCode = 31 * hashCode + (change.calcHashCode())
  return hashCode
}

@ApiStatus.Experimental
@ApiStatus.Internal
fun Change.calcHashCode(): Int = CODE_REVIEW_CHANGE_HASHING_STRATEGY.hashCode(this)

@ApiStatus.Experimental
@ApiStatus.Internal
val CODE_REVIEW_CHANGE_HASHING_STRATEGY: HashingStrategy<Change> = HashingStrategy.identity()