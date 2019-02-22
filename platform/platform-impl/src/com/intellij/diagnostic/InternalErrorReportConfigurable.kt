// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "InternalErrorReportConfigurable", storages = [(Storage(value = "internalReportError.xml"))])
internal class InternalErrorReportConfigurable : PersistentStateComponent<InternalErrorReportConfigurable.State> {
  private var myState: State = State()

  override fun getState(): State = myState

  override fun loadState(state: State) {
    myState = state
  }

  fun getDevelopersList(): List<Developer> {
    return myState.developersList.toList()
  }

  fun setDevelopersList(developersList: List<Developer>) {
    myState = State(developersList.toList(), System.currentTimeMillis())
  }

  fun isDevelopersListValid(): Boolean {
    val developersListObsolete = System.currentTimeMillis() - myState.developersUpdateTimestamp >= DEVELOPERS_OBSOLESCENCE_MILLIS
    return !developersListObsolete && myState.developersList.isNotEmpty()
  }

  fun getDevelopersUpdateTimestamp(): Long = myState.developersUpdateTimestamp

  private companion object {
    private const val DEVELOPERS_OBSOLESCENCE_MILLIS = 24L * 60 * 60 * 1000 // 24 hours

    @JvmStatic
    fun getInstance(): InternalErrorReportConfigurable = ServiceManager.getService(InternalErrorReportConfigurable::class.java)
  }

  internal data class State(
    var developersList: List<Developer> = emptyList(),
    var developersUpdateTimestamp: Long = 0
  )
}
