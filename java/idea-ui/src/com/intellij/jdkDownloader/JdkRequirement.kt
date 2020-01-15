// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkDownloader

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.UnknownSdk
import com.intellij.util.lang.JavaVersion


interface JdkRequirement {
  fun matches(sdk: Sdk): Boolean
  fun matches(sdk: JdkItem): Boolean
  fun matches(version: String): Boolean
}

object JdkRequirements {
  private val LOG = logger<JdkRequirements>()

  private interface VersionMatcher {
    fun matchVersion(versionString: String) : Boolean
    override fun toString(): String
  }

  private fun sameMajorVersionMatcher(parsed: JavaVersion): VersionMatcher {
    return object: VersionMatcher {
      override fun toString() = "it >= $parsed && same major version"
      override fun matchVersion(versionString: String): Boolean {
        val it = JavaVersion.tryParse(versionString) ?: return false
        return it >= parsed && it.feature == parsed.feature
      }
    }
  }

  private fun strictVersionMatcher(parsed: JavaVersion): VersionMatcher {
    return object: VersionMatcher {
      override fun toString() = "it == $parsed"
      override fun matchVersion(versionString: String): Boolean {
        val it = JavaVersion.tryParse(versionString) ?: return false
        return it == parsed
      }
    }
  }

  fun parseRequirement(request: UnknownSdk) : JdkRequirement? {
    val name = request.sdkName
    if (name != null) {
      return parseRequirement(name)
    }

    //TODO: include version filter here
    return null
  }

  fun parseRequirement(request: String): JdkRequirement? {
    try {
      val versionMatcher = if (request.trim().startsWith("=")) ::strictVersionMatcher else ::sameMajorVersionMatcher
      val text = request.trimStart('=').trim()

      //case 1. <vendor>-<version>
      run {
        val parts = text.split("-", " ").map(String::trim).filterNot(String::isBlank)
        if (parts.size != 2) return@run
        val (vendor, version) = parts
        val javaVersion = JavaVersion.tryParse(version) ?: return null
        val matcher = versionMatcher(javaVersion)

        return object : JdkRequirement {
          override fun matches(sdk: Sdk) = false /*TODO[jo]*/
          override fun matches(version: String) = false /*TODO[jo]*/
          override fun matches(sdk: JdkItem) = matcher.matchVersion(sdk.versionString) && sdk.product.matchesVendor(vendor)
          override fun toString() = "JdkRequirement { $vendor && $matcher }"
        }
      }

      //case 2. It is just a version
      run {
        val javaVersion = JavaVersion.tryParse(text) ?: return null
        val matcher = versionMatcher(javaVersion)
        return object : JdkRequirement {
          override fun matches(sdk: Sdk) = sdk.versionString?.let { matches(it) } == true
          override fun matches(sdk: JdkItem) = matches(sdk.versionString)
          override fun matches(version: String) = matcher.matchVersion(version)
          override fun toString() = "JdkRequirement { $matcher }"
        }
      }
    }
    catch (t: Throwable) {
      LOG.warn("Failed to parse requirement $request. ${t.message}", t)
      return null
    }
  }
}

