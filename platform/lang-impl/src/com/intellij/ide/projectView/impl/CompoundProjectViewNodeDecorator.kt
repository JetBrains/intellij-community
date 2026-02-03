// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

private val EMPTY: ProjectViewNodeDecorator = object : ProjectViewNodeDecorator {
  override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
  }
}

private val KEY = Key.create<ProjectViewNodeDecorator?>("ProjectViewNodeDecorator")
private val LOG = logger<CompoundProjectViewNodeDecorator>()

/**
 * This class is intended to combine all decorators for batch usages.
 */
@ApiStatus.Internal
class CompoundProjectViewNodeDecorator private constructor(private val project: Project) : ProjectViewNodeDecorator {
  companion object {
    @JvmField
    internal val EP: ProjectExtensionPointName<ProjectViewNodeDecorator> =
      ProjectExtensionPointName<ProjectViewNodeDecorator>("com.intellij.projectViewNodeDecorator")

    /**
     * @return a shared instance for the specified project
     */
    @JvmStatic
    fun get(project: Project?): ProjectViewNodeDecorator {
      if (project == null || project.isDisposed() || project.isDefault) {
        return EMPTY
      }

      project.getUserData(KEY)?.let {
        return it
      }

      val provider = CompoundProjectViewNodeDecorator(project)
      project.putUserData(KEY, provider)
      return provider
    }
  }

  override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
    if (project.isDisposed()) {
      return
    }

    for (decorator in EP.getExtensions(project)) {
      try {
        decorator.decorate(node, data)
      }
      catch (e: IndexNotReadyException) {
        throw ProcessCanceledException(e)
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Exception) {
        LOG.warn("unexpected error in $decorator", e)
      }
    }
  }
}
