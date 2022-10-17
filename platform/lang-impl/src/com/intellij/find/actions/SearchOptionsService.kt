// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions

import com.intellij.find.actions.SearchOptionsService.MyState
import com.intellij.find.actions.SearchOptionsService.SearchVariant
import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageOptions
import com.intellij.find.usages.impl.AllSearchOptions
import com.intellij.find.usages.impl.hasTextSearchStrings
import com.intellij.openapi.components.*
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope

// TODO persist custom options with the same mechanism as in PersistentStateComponent
@State(name = "SearchOptions", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
@Service
internal class SearchOptionsService : PersistentStateComponent<MyState> {

  private val myFuState = HashMap<String, PersistedSearchOptions>()
  private val mySuState = HashMap<String, PersistedSearchOptions>()

  enum class SearchVariant {
    FIND_USAGES,
    SHOW_USAGES,
  }

  private fun state(variant: SearchVariant) = when (variant) {
    SearchVariant.FIND_USAGES -> myFuState
    SearchVariant.SHOW_USAGES -> mySuState
  }

  @Synchronized
  fun getSearchOptions(variant: SearchVariant, key: String): PersistedSearchOptions {
    return state(variant)[key] ?: defaultOptions
  }

  @Synchronized
  fun setSearchOptions(variant: SearchVariant, key: String, options: PersistedSearchOptions) {
    if (options == defaultOptions) {
      state(variant).remove(key)
    }
    else {
      state(variant)[key] = options
    }
  }

  class MyState(
    var fuState: ArrayList<MyStateEntry>? = null,
    var suState: ArrayList<MyStateEntry>? = null,
  )

  class MyStateEntry(
    var key: String? = null,
    var usages: Boolean = true,
    var textSearch: Boolean = true,
  )

  @Synchronized
  override fun getState(): MyState = MyState(
    fuState = getStateEntries(myFuState),
    suState = getStateEntries(mySuState),
  )

  private fun getStateEntries(state: Map<String, PersistedSearchOptions>): ArrayList<MyStateEntry> {
    return state.mapTo(ArrayList()) { (key, options) ->
      MyStateEntry(key, options.usages, options.textSearch)
    }
  }

  @Synchronized
  override fun loadState(state: MyState) {
    loadStateEntries(myFuState, state.fuState)
    loadStateEntries(mySuState, state.suState)
  }

  private fun loadStateEntries(state: MutableMap<String, PersistedSearchOptions>, entries: List<MyStateEntry>?) {
    state.clear()
    if (entries == null) {
      return
    }
    for (entry in entries) {
      val key = entry.key ?: continue
      state[key] = PersistedSearchOptions(entry.usages, entry.textSearch)
    }
  }
}

internal class PersistedSearchOptions(
  val usages: Boolean,
  val textSearch: Boolean,
)

internal fun getSearchOptions(
  variant: SearchVariant,
  target: SearchTarget,
  selectedScope: SearchScope,
): AllSearchOptions {
  val persistedOptions: PersistedSearchOptions = searchOptionsService().getSearchOptions(variant, target.targetKey())
  val scopeToUse = target.maximalSearchScope as? LocalSearchScope
                   ?: selectedScope
  return AllSearchOptions(
    options = UsageOptions.createOptions(persistedOptions.usages, scopeToUse),
    textSearch = if (target.hasTextSearchStrings()) persistedOptions.textSearch else null,
  )
}

internal fun setSearchOptions(variant: SearchVariant, target: SearchTarget, allOptions: AllSearchOptions) {
  val newOptions = PersistedSearchOptions(allOptions.options.isUsages, allOptions.textSearch ?: true)
  searchOptionsService().setSearchOptions(variant, target.targetKey(), newOptions)
}

private val defaultOptions = PersistedSearchOptions(true, true)

private fun searchOptionsService(): SearchOptionsService = service()

private fun SearchTarget.targetKey(): String = javaClass.name
