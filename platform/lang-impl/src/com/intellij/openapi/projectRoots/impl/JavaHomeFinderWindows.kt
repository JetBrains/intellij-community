// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ConvertSecondaryConstructorToPrimary")
package com.intellij.openapi.projectRoots.impl

import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Bitness
import com.intellij.openapi.util.io.WindowsRegistryUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import java.nio.file.Paths
import java.util.TreeSet
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.exists
import kotlin.text.RegexOption.IGNORE_CASE
import kotlin.text.RegexOption.MULTILINE

@Internal
class JavaHomeFinderWindows : JavaHomeFinderBasic {
  companion object {
    const val defaultJavaLocation: String = "C:\\Program Files"

    @Suppress("SpellCheckingInspection")
    private val regCommand = listOf("reg", "query", """HKLM\SOFTWARE\JavaSoft\JDK""", "/s", "/v", "JavaHome")

    /**
     * Vendor directories under `Program Files` where JDK installers put their JDKs.
     * Most of these installers do not write `JavaSoft` registry keys by default.
     * For example, `winget install Microsoft.OpenJDK.17` installs into `C:\Program Files\Microsoft\jdk-17.x-hotspot`.
     */
    private val vendorDirs = listOf(
      "Microsoft",
      "Eclipse Adoptium",
      "Eclipse Foundation",
      "AdoptOpenJDK",
      "Zulu",
      "BellSoft",
      "Amazon Corretto",
      "Semeru",
      "SapMachine",
      "RedHat",
    )

    private val javaHomePattern = Regex("""^\s+JavaHome\s+REG_SZ\s+(\S.+\S)\s*$""", setOf(MULTILINE, IGNORE_CASE))

    private val logger: Logger = Logger.getInstance(JavaHomeFinderWindows::class.java)

    fun gatherHomePaths(text: CharSequence): Set<String> {
      val paths = TreeSet<String>()
      var m: MatchResult? = javaHomePattern.find(text)
      while (m != null) {
        m.groups[1]?.run { paths += value }
        m = m.next()
      }
      return paths
    }
  }

  private val processRunner: (cmd: List<String>) -> CharSequence

  @JvmOverloads
  constructor(
    registeredJdks: Boolean,
    wslJdks: Boolean,
    systemInfoProvider: JavaHomeFinder.SystemInfoProvider,
    processRunner: (cmd: List<String>) -> CharSequence = { cmd -> WindowsRegistryUtil.readRegistry(cmd.joinToString(" ")) },
  ) : super(systemInfoProvider) {
    this.processRunner = processRunner

    if (registeredJdks) {
      /** Whether the OS is 64-bit (**important**: it's not the same as [com.intellij.util.system.CpuArch]). */
      val os64bit = !systemInfoProvider.getEnvironmentVariable("ProgramFiles(x86)").isNullOrBlank()
      if (os64bit) {
        registerFinder(this::readRegisteredLocationsOS64J64)
        registerFinder(this::readRegisteredLocationsOS64J32)
      }
      else {
        registerFinder(this::readRegisteredLocationsOS32J32)
      }
    }
    registerFinder(this::guessPossibleLocations)
    if (wslJdks) {
      val installedDistributions = try {
        WslDistributionManager.getInstance().installedDistributions
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (t: Throwable) {
        thisLogger().warn(IllegalStateException("Unable to get WSL distributions list: ${t.message}", t))
        emptyList()
      }

      for (distro in installedDistributions) {
        try {
          val wslFinder = JavaHomeFinderWsl(distro)
          registerFinder { wslFinder.findExistingJdks() }
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (t: Throwable) {
          thisLogger().warn(IllegalStateException("Unable to connect to WSL distribution '${distro.id}': ${t.message}", t))
        }
      }
    }
  }

  private fun readRegisteredLocationsOS64J64() = readRegisteredLocations(Bitness.x64)
  private fun readRegisteredLocationsOS64J32() = readRegisteredLocations(Bitness.x32)
  private fun readRegisteredLocationsOS32J32() = readRegisteredLocations(null)

  private fun readRegisteredLocations(b: Bitness?): Set<String> {
    val cmd =
      when (b) {
        null -> regCommand
        Bitness.x32 -> regCommand + "/reg:32"
        Bitness.x64 -> regCommand + "/reg:64"
      }
    try {
      val registryLines: CharSequence = processRunner(cmd)
      val registeredPaths = gatherHomePaths(registryLines)
      val folders: MutableSet<Path> = TreeSet()
      for (rp in registeredPaths) {
        val r = Paths.get(rp)
        val parent = r.parent
        if (parent != null && parent.exists()) {
          folders.add(parent)
        }
        else if (r.exists()) {
          folders.add(r)
        }
      }
      return scanAll(folders, true)
    }
    catch (ie: InterruptedException) {
      return emptySet()
    }
    catch (e: CancellationException) {
      return emptySet()
    }
    catch (e: Exception) {
      logger.warn("Unable to detect registered JDK using the following command: $cmd", e)
      return emptySet()
    }
  }

  private fun guessPossibleLocations(): Set<String> {
    val fsRoots = systemInfo.fsRoots
    val roots: MutableSet<Path> = HashSet()
    for (root in fsRoots) {
      ProgressManager.checkCanceled()
      if (!root.exists()) {
        continue
      }

      roots.add(root.resolve("Program Files/Java"))
      roots.add(root.resolve("Program Files (x86)/Java"))
      roots.add(root.resolve("Java"))
      // Missing vendor dirs are harmless: scanAll skips them.
      for (vendor in vendorDirs) {
        roots.add(root.resolve("Program Files/$vendor"))
        roots.add(root.resolve("Program Files (x86)/$vendor"))
      }
    }
    getPathInUserHome(".jdks")?.let { roots.add(it) }
    return scanAll(roots, true)
  }
}
