package com.intellij.microservices.endpoints

import com.intellij.icons.AllIcons
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

interface EndpointsModuleEntity {
  val name: String
  val icon: Icon
}

// Used for UI customization in Rider
interface EndpointsProjectModel {
  @get:Nls(capitalization = Nls.Capitalization.Title)
  val moduleDisplayName: String
  val moduleQueryTag: String

  @get:NlsContexts.PopupTitle
  val selectModulesTitle: String

  @get:NlsActions.ActionText
  val groupByModuleTitleShort: String

  @get:NlsActions.ActionText
  val groupByModuleTitleFull: String

  fun getModuleEntities(project: Project): Collection<EndpointsModuleEntity>

  fun isTestModule(entity: EndpointsModuleEntity): Boolean

  fun createFilter(entity: EndpointsModuleEntity, fromLibraries: Boolean, fromTests: Boolean): EndpointsFilter

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<EndpointsProjectModel> =
      ExtensionPointName.create("com.intellij.microservices.endpointsProjectModel")
  }
}

@ApiStatus.Internal
class DefaultEndpointsModule(val module: Module) : EndpointsModuleEntity {
  override val name: String
    get() = module.name

  override val icon: Icon
    get() = AllIcons.Nodes.Module
}