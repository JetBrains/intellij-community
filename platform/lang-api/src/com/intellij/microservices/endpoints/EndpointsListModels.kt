package com.intellij.microservices.endpoints

import com.intellij.microservices.url.UrlTargetInfo

interface EndpointsListItem

interface EndpointsModuleItem {
  val module: EndpointsModuleEntity?
}

interface EndpointsElementItem<G : Any, E : Any> : EndpointsListItem {
  val module: EndpointsModuleEntity?
  val provider: EndpointsProvider<G, E>
  val group: G
  val endpoint: E
  val isValid: Boolean

  /**
   * Return [UrlTargetInfo]s for [group] and [endpoint]
   * if [provider] is [EndpointsUrlTargetProvider]
   */
  fun getUrlTargetInfos(): Iterable<UrlTargetInfo>? = null
}