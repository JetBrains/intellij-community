package com.intellij.codeInsight.documentation.render

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus

/**
 * Provides a [DocRenderer] object for use in [DocRenderItemManagerImpl].
 *
 * Is important for the Rider implementation of the "Render Documentation" feature, since it needs to use custom
 * [DocRenderLinkActivationHandler]s inside its [DocRenderer]s.
 */
@ApiStatus.Internal
interface DocRendererProvider {
  companion object {
    @JvmStatic fun getInstance(): DocRendererProvider = ApplicationManager.getApplication().getService(DocRendererProvider::class.java)
  }

  fun provideDocRenderer(item: DocRenderItem): DocRenderer
}

/**
 * A default implementation for the [DocRendererProvider] interface.
 */
@ApiStatus.Internal
class DocRendererProviderImpl : DocRendererProvider {

  override fun provideDocRenderer(item: DocRenderItem) = DocRenderer(item)

}