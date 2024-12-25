package com.intellij.microservices.client.generator

import com.intellij.microservices.oas.OpenApiSpecification
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.Nls

/**
 * Provides information for an Examples tab in the Endpoints side panel.
 */
@Experimental
interface ClientGenerator {
  @get:Nls
  val title: String

  val availableClientSettings: AvailableClientSettings
    get() = EMPTY_SETTINGS

  fun generate(
    project: Project,
    openApiSpecification: OpenApiSpecification,
  ): ClientExample?

  companion object {
    val EP_NAME: ExtensionPointName<ClientGenerator> =
      ExtensionPointName.create("com.intellij.microservices.clientGenerator")
  }
}

data class ClientExample(val text: String, val fileType: FileType) {
  companion object {
    fun fromFileExtension(text: String, extension: String): ClientExample {
      val fileType = FileTypeManager.getInstance().getFileTypeByExtension(extension)

      return ClientExample(text, fileType)
    }
  }
}