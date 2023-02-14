// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Disposer

@Service(Service.Level.PROJECT)
class CombinedDiffModelRepository : Disposable {

  private val models = hashMapOf<String, CombinedDiffModel>()

  fun registerModel(sourceId: String, model: CombinedDiffModel) {
    dispose(sourceId)

    Disposer.register(model.ourDisposable) { models.remove(sourceId) }
    models[sourceId] = model
  }

  fun findModel(sourceId: String) = models[sourceId]

  fun dispose(sourceId: String) = models.remove(sourceId)?.ourDisposable?.let(Disposer::dispose)

  override fun dispose() {
    models.clear()
  }
}
