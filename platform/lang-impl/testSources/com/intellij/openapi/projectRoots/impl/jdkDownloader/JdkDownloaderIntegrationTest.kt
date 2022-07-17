// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.idea.TestFor
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.system.CpuArch
import org.junit.Assert
import org.junit.Test
import java.lang.RuntimeException

class JdkDownloaderIntegrationTest : BasePlatformTestCase() {
  @Test
  fun `test default model can be downloaded and parsed`() {
    lateinit var lastError: Throwable
    repeat(5) {
      val result = runCatching {
        val data = JdkListDownloader.getInstance().downloadForUI(null)
        Assert.assertTrue(data.isNotEmpty())
        Assert.assertTrue(data.all { it.sharedIndexAliases.isNotEmpty() })

        //IDEA-253533
        buildJdkDownloaderModel(data)
      }
      if (result.isSuccess) return
      lastError = result.exceptionOrNull()!!

      if (lastError.message?.startsWith("Failed to download list of available JDKs") == true) {
        Thread.sleep(5000)
      }
      else throw lastError
    }
    throw RuntimeException("Failed to download JDK list within several tries", lastError)
  }

  @Test
  fun `test default model should have JBR`() {
    lateinit var lastError: Throwable
    repeat(5) {
      val result = runCatching {
        val data = JdkListDownloader.getInstance().downloadModelForJdkInstaller(null)
        val jbr = data.filter { it.matchesVendor("jbr") }
        Assert.assertTrue(jbr.isNotEmpty() || SystemInfo.isLinux && CpuArch.isArm64())
      }
      if (result.isSuccess) return
      lastError = result.exceptionOrNull()!!

      if (lastError.message?.startsWith("Failed to download list of available JDKs") == true) {
        Thread.sleep(5000)
      }
      else throw lastError
    }
    throw RuntimeException("Failed to download JDK list within several tries", lastError)
  }

  @Test
  fun `test default non-UI  model is cached`() {
    lateinit var lastError: Throwable
    repeat(5) {

      val downloader = JdkListDownloader.getInstance()
      val packs = List(10) { runCatching { downloader.downloadModelForJdkInstaller(null) }.getOrNull() }.filterNotNull()

      if (packs.size < 3) {
        return@repeat
      }

      //must return cached JdkItem objects
      packs.forEach { p1 ->
        packs.forEach { p2 ->
          Assert.assertEquals(p1.size, p2.size)
          for (i in p1.indices) {
            Assert.assertSame(p1[i], p2[i])
          }
        }
      }
      return
    }
    throw RuntimeException("Failed to download JDK list within several tries", lastError)
  }

  @Test
  @TestFor(issues = ["IDEA-252237"])
  fun `test default model for ui is not cached`() {
    lateinit var lastError: Throwable
    repeat(5) {

      val downloader = JdkListDownloader.getInstance()
      val packs = List(2) { runCatching { downloader.downloadForUI(null) }.getOrNull() }.filterNotNull()

      if (packs.size < 2) {
        return@repeat
      }

      //must not return cached JdkItem objects
      packs.forEach { p1 ->
        packs.forEach { p2 ->
          if (p1 !== p2) {
            Assert.assertEquals(p1.size, p2.size)
            for (i in p1.indices) {
              Assert.assertNotSame(p1[i], p2[i])
              Assert.assertEquals(p1[i], p2[i])
            }
          }
        }
      }
      return
    }
    throw RuntimeException("Failed to download JDK list within several tries", lastError)
  }
}
