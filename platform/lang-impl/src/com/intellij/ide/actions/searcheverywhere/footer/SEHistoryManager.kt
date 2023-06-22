// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.footer

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

sealed class SEHistoryManager : PersistentStateComponent<SEHistoryManager.State> {
  class State {
    @XCollection(style = XCollection.Style.v2)
    var ids: MutableSet<String>
    
    constructor() {
      ids = mutableSetOf()
    }

    internal constructor(ids: MutableSet<String>) {
      this.ids = ids
    }
  }

  private var _state = State()
  override fun getState() = _state
  override fun loadState(state: State) { _state = state }
}

@Service(Service.Level.PROJECT)
@State(name = "ClassHistoryManager", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ClassHistoryManager : PersistentStateComponent<ClassHistoryManager.State> {
  class State {
    @Tag("qname2module")
    @MapAnnotation
    var qname2Modules: MutableMap<String, MutableList<String>>

    @Tag("qname2name")
    @MapAnnotation
    var qname2Name: MutableMap<String, String>

    constructor() {
      qname2Modules = mutableMapOf()
      qname2Name = mutableMapOf()
    }

    internal constructor(ids2Module: MutableMap<String, MutableList<String>>, qname2Name: MutableMap<String, String>) {
      this.qname2Modules = ids2Module
      this.qname2Name = qname2Name
    }

    fun putOrAddQname2Modules(name: String, module: String) {
      qname2Modules.getOrPut(name) { mutableListOf() }.add(module)
    }
  }

  private var _state = State()
  override fun getState() = _state
  override fun loadState(state: State) { _state = state }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<ClassHistoryManager>()
  }
}

@Service(Service.Level.APP)
@State(name = "ActionHistoryManager", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
class ActionHistoryManager : SEHistoryManager() {
  companion object {
    @JvmStatic
    fun getInstance() = service<ActionHistoryManager>()
  }
}

@Service(Service.Level.PROJECT)
@State(name = "FileHistoryManager", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class FileHistoryManager : SEHistoryManager() {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<FileHistoryManager>()
  }
}