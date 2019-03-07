// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "InternalErrorReportConfigurable", storages = [(Storage(value = "internalReportError.xml"))])
internal class InternalErrorReportConfigurable : PersistentStateComponent<InternalErrorReportConfigurable.State> {
  private var myState: State? = null

  override fun getState(): State? = myState

  override fun loadState(state: State) {
    myState = state
  }

  var developers: List<Developer>
    get() = myState?.developers?.toList() ?: emptyList()
    set(developersList) {
      myState = State(developersList.toList(), System.currentTimeMillis())
    }

  fun isDevelopersListObsolete(): Boolean {
    val state = myState ?: return false
    val developersListObsolete = System.currentTimeMillis() - state.developersUpdateTimestamp >= UPDATE_INTERVAL
    return !developersListObsolete && state.developers.isNotEmpty()
  }

  fun getDevelopersUpdateTimestamp(): Long? = myState?.developersUpdateTimestamp

  private companion object {
    private const val UPDATE_INTERVAL = 24L * 60 * 60 * 1000 // 24 hours

    @JvmStatic
    val instance: InternalErrorReportConfigurable
      get() = ServiceManager.getService(InternalErrorReportConfigurable::class.java)
  }

  internal data class State(
    var developers: List<Developer>,
    var developersUpdateTimestamp: Long
  ) {
    private constructor(): this(emptyList(), 0) // need for xml serialization
  }
}
