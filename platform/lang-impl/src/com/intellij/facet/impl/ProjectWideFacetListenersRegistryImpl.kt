// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl

import com.intellij.facet.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

@Suppress("ObjectLiteralToLambda")
internal class ProjectWideFacetListenersRegistryImpl(private val project: Project) : ProjectWideFacetListenersRegistry() {
  override fun <F : Facet<*>> registerListener(typeId: FacetTypeId<F>, listener: ProjectWideFacetListener<out F>) {
    FacetEventsPublisher.getInstance(project).registerListener(typeId, ProjectWideFacetListenerWrapper(listener))
  }

  override fun <F : Facet<*>> unregisterListener(
    typeId: FacetTypeId<F>,
    listener: ProjectWideFacetListener<out F>
  ) {
    FacetEventsPublisher.getInstance(project).unregisterListener(typeId, ProjectWideFacetListenerWrapper(listener))
  }

  override fun <F : Facet<*>> registerListener(
    typeId: FacetTypeId<F>,
    listener: ProjectWideFacetListener<out F>,
    parentDisposable: Disposable
  ) {
    registerListener(typeId, listener)
    Disposer.register(parentDisposable, object : Disposable {
      override fun dispose() {
        unregisterListener(typeId, listener)
      }
    })
  }

  override fun registerListener(listener: ProjectWideFacetListener<Facet<*>>) {
    FacetEventsPublisher.getInstance(project).registerListener(null, ProjectWideFacetListenerWrapper(listener))
  }

  override fun unregisterListener(listener: ProjectWideFacetListener<Facet<*>>) {
    FacetEventsPublisher.getInstance(project).unregisterListener(null, ProjectWideFacetListenerWrapper(listener))
  }

  @Suppress("ObjectLiteralToLambda")
  override fun registerListener(listener: ProjectWideFacetListener<Facet<*>>, parentDisposable: Disposable) {
    registerListener(listener)
    Disposer.register(parentDisposable, object : Disposable {
      override fun dispose() {
        if (!project.isDisposed()) {
          unregisterListener(listener)
        }
      }
    })
  }
}

private class ProjectWideFacetListenerWrapper<F : Facet<*>>(private val listener: ProjectWideFacetListener<F>) : ProjectFacetListener<F> {
  override fun firstFacetAdded(project: Project) {
    listener.firstFacetAdded()
  }

  override fun facetAdded(facet: F) {
    listener.facetAdded(facet)
  }

  override fun beforeFacetRemoved(facet: F) {
    listener.beforeFacetRemoved(facet)
  }

  override fun facetRemoved(facet: F, project: Project) {
    listener.facetRemoved(facet)
  }

  override fun allFacetsRemoved(project: Project) {
    listener.allFacetsRemoved()
  }

  override fun facetConfigurationChanged(facet: F) {
    listener.facetConfigurationChanged(facet)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    return listener == (other as ProjectWideFacetListenerWrapper<*>).listener
  }

  override fun hashCode(): Int = listener.hashCode()
}
