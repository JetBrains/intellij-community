// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkDownloader

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.lang.JavaVersion


interface JdkRequirement {
  fun matches(sdk: Sdk): Boolean
  fun matches(sdk: JdkItem): Boolean
  fun matches(version: String): Boolean
}

object JdkRequirements {
  private val LOG = logger<JdkAuto>()

  private fun String.isVersionAtLeast(parsed: JavaVersion): Boolean {
    val it = JavaVersion.tryParse(this) ?: return false
    return it >= parsed && it.feature == parsed.feature
  }

  fun parseRequirement(text: String): JdkRequirement? {
    try {
      //case 1. <vendor>-<version>
      run {
        val parts = text.split("-").map(String::trim).filterNot(String::isBlank)
        if (parts.size != 2) return@run
        val (vendor, version) = parts
        val javaVersion = JavaVersion.tryParse(version) ?: return null

        return object : JdkRequirement {
          override fun matches(sdk: Sdk) = false /*TODO[jo]*/
          override fun matches(version: String) = false /*TODO[jo]*/
          override fun matches(sdk: JdkItem) = sdk.versionString.isVersionAtLeast(javaVersion)
                                               && sdk.product.matchesVendor(vendor)
          override fun toString() = "JdkRequirement { $vendor && it >= $javaVersion }"
        }
      }

      //case 2. It is just a version
      run {
        val parsed = JavaVersion.tryParse(text)
        if (parsed != null) {
          return object : JdkRequirement {
            override fun matches(sdk: Sdk) = sdk.versionString?.let { matches(it) } == true
            override fun matches(sdk: JdkItem) = matches(sdk.versionString)
            override fun matches(version: String) = version.isVersionAtLeast(parsed)
            override fun toString() = "JdkRequirement { it >= $parsed }"
          }
        }
      }

      return null
    }
    catch (t: Throwable) {
      LOG.warn("Failed to parse requirement $text. ${t.message}", t)
      return null
    }
  }
}

