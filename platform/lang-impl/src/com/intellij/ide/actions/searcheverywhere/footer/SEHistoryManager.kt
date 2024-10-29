// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.footer

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed class SEHistoryManager : PersistentStateComponent<SEHistoryManager.State> {
  class State {
    @XCollection(style = XCollection.Style.v2)
    var ids: RecentSet<String>

    constructor() {
      ids = RecentSet()
    }

    internal constructor(ids: RecentSet<String>) {
      this.ids = ids
    }
  }

  private var _state = State()
  override fun getState() = _state
  override fun loadState(state: State) { _state = state }
}

@ApiStatus.Internal
open class RecentSet<T> : LinkedHashSet<T>() {
  override fun add(e: T): Boolean {
    val wasThere = remove(e)
    super.add(e)
    return !wasThere
  }
}

@ApiStatus.Internal
@Service(Service.Level.APP)
@State(name = "ActionHistoryManager", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
class ActionHistoryManager : SEHistoryManager() {
  companion object {
    @JvmStatic
    fun getInstance() = service<ActionHistoryManager>()
  }
}