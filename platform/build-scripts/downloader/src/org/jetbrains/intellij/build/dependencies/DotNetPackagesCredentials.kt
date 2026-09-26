// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.tryGetSingleChildElement
import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path

@Suppress("unused")
object DotNetPackagesCredentials {
  @JvmStatic
  fun setupSystemCredentials(): Boolean {
    try {
      if (loadFromEnvVars()) {
        println("* DotNet packages credentials loaded from environment variable")
        return true
      }
      if (loadFromNuGetConfig()) {
        println("* DotNet packages credentials loaded from NuGet.Config")
        return true
      }
    }
    catch (t: Throwable) {
      val writer = StringWriter()
      t.printStackTrace(PrintWriter(writer))
      error(writer.buffer.toString())
    }
    println("* DotNet packages credentials not loaded")
    return false
  }

  private fun loadFromEnvVars(): Boolean {
    val credentialsFromEnvVar = System.getenv("NuGetPackageSourceCredentials_dotnet_build_space") ?: return false
    val parts = credentialsFromEnvVar.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    var isUsernameSet = false
    var isPasswordSet = false
    for (part in parts) {
      val subParts = part.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      if ("Username" == subParts[0]) {
        System.setProperty(BuildDependenciesConstants.JPS_AUTH_SPACE_USERNAME, subParts[1])
        isUsernameSet = true
      }
      else if ("Password" == subParts[0]) {
        System.setProperty(BuildDependenciesConstants.JPS_AUTH_SPACE_PASSWORD, subParts[1])
        isPasswordSet = true
      }
    }
    return isUsernameSet && isPasswordSet
  }

  @Throws(IOException::class, SAXException::class)
  private fun loadFromNuGetConfig(): Boolean {
    val nuGetConfig: File = if (BuildDependenciesUtil.isWindows) {
      Path.of(System.getenv("APPDATA"), "NuGet", "NuGet.Config").toFile()
    }
    else {
      Path.of(System.getProperty("user.home"), ".nuget", "NuGet", "NuGet.Config").toFile()
    }
    if (!nuGetConfig.exists()) {
      return false
    }
    val documentBuilder = BuildDependenciesUtil.createDocumentBuilder()
    val document = documentBuilder.parse(nuGetConfig)
    val packageSourceCredentialsElement = document.documentElement.tryGetSingleChildElement("packageSourceCredentials")
                                          ?: return false
    val dotNetSpaceBuild = packageSourceCredentialsElement.tryGetSingleChildElement("dotnet_build_space")
                           ?: return false
    var isUsernameSet = false
    var isPasswordSet = false
    for (i in 0 until dotNetSpaceBuild.childNodes.length) {
      if (dotNetSpaceBuild.childNodes.item(i) is Element) {
        val childElement = dotNetSpaceBuild.childNodes.item(i) as Element
        if ("add" == childElement.tagName) {
          val key = childElement.getAttribute("key")
          val value = childElement.getAttribute("value")
          if ("Username" == key) {
            System.setProperty(BuildDependenciesConstants.JPS_AUTH_SPACE_USERNAME, value)
            isUsernameSet = true
          }
          else if ("ClearTextPassword" == key) {
            System.setProperty(BuildDependenciesConstants.JPS_AUTH_SPACE_PASSWORD, value)
            isPasswordSet = true
          }
        }
      }
    }
    return isUsernameSet && isPasswordSet
  }
}
