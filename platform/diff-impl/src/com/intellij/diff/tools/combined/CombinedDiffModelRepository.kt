// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Disposer

@Service(Service.Level.PROJECT)
class CombinedDiffModelRepository : Disposable {

  private val models: HashMap<String, CombinedDiffModel> = hashMapOf()

  fun registerModel(sourceId: String, model: CombinedDiffModel) {
    disposeIfRegistered(sourceId)

    Disposer.register(model.ourDisposable) {
      models.remove(sourceId)
    }
    models[sourceId] = model
  }

  fun findModel(sourceId: String): CombinedDiffModel? = models[sourceId]

  private fun disposeIfRegistered(sourceId: String) {
    val diffModel = models.remove(sourceId) ?: return
    Disposer.dispose(diffModel.ourDisposable)
  }

  override fun dispose() {
    models.clear() // TODO: why not dispose?
  }
}
