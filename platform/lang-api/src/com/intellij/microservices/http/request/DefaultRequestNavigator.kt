package com.intellij.microservices.http.request

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.microservices.MicroservicesBundle
import com.intellij.microservices.url.HttpMethods
import com.intellij.openapi.project.Project
import com.intellij.util.Urls
import javax.swing.Icon

class DefaultRequestNavigator : RequestNavigator {
  override val id: String
    get() = "DefaultRequestNavigator"

  override fun accept(request: NavigatorHttpRequest): Boolean = HttpMethods.GET == request.requestMethod

  override fun navigate(project: Project, request: NavigatorHttpRequest, hint: String) {
    if (accept(request)) {
      val parametersMap = request.params.associateBy({ it.first }, { it.second })
      val url = Urls.newFromEncoded(request.url).addParameters(parametersMap)
      BrowserUtil.open(url.toExternalForm())
    }
  }

  override val icon: Icon
    get() = AllIcons.Nodes.PpWeb
  override val displayText: String
    get() = MicroservicesBundle.message("microservices.open.in.browser.action.name")
}