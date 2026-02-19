package com.intellij.microservices.http.request

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.microservices.MicroservicesBundle
import com.intellij.microservices.url.HttpMethods
import com.intellij.openapi.project.Project
import com.intellij.util.Urls
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
class DefaultRequestNavigator : RequestNavigator {
  override val id: String = "DefaultRequestNavigator"
  override val icon: Icon = AllIcons.Nodes.PpWeb

  override fun accept(request: NavigatorHttpRequest): Boolean = HttpMethods.GET == request.requestMethod

  override fun navigate(project: Project, request: NavigatorHttpRequest, hint: String) {
    if (accept(request)) {
      val parametersMap = request.params.associateBy({ it.first }, { it.second })
      val url = Urls.newFromEncoded(request.url).addParameters(parametersMap)
      BrowserUtil.open(url.toExternalForm())
    }
  }

  override val displayText: String
    get() = MicroservicesBundle.message("microservices.open.in.browser.action.name")
}