package com.intellij.microservices.http.request

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import javax.swing.Icon

interface RequestNavigator {
  companion object {
    private val EP_NAME: ExtensionPointName<RequestNavigator> = ExtensionPointName.create("com.intellij.microservices.requestNavigator")

    @JvmStatic
    fun getRequestNavigators(): List<RequestNavigator> = EP_NAME.extensionList

    @JvmStatic
    fun getRequestNavigators(request: NavigatorHttpRequest): List<RequestNavigator> = EP_NAME.extensionList.filter { it.accept(request) }
  }

  val id: String
  val icon: Icon?
  val displayText: String
    @NlsContexts.ListItem
    get
  val navigationGroupName: String
    @NlsContexts.Separator
    get() = ""

  fun accept(request: NavigatorHttpRequest): Boolean
  fun navigate(project: Project, request: NavigatorHttpRequest, hint: String)

  fun hasTarget(): Boolean = false
  @NlsContexts.ListItem
  fun getNavigationMessage(request: NavigatorHttpRequest): String = ""
}