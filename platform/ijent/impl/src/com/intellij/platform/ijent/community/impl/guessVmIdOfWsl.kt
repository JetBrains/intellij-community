// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.platform.ijent.IjentLogger.CONN_MGR_LOG
import java.util.UUID

/**
 * There is a reliable way to get the VmId of WSL by the use of `HcsEnumerateComputeSystems`, see the script `ComputeVmId.ps1`.
 * Unfortunately, this approach requires elevated privileges.
 * This is very inconvenient: WSL changes its VmId on restarts, and they can happen quite frequently:
 * either on explicit `wsl --shutdown`, or because WSL simply dies because no one touches it. We would have to annoy the user
 * by frequent request of administrator access.
 *
 * Instead, we employ a _heuristic_: VmId is often passed as an argument to other Windows processes.
 * We read the data of these processes and try to find VmId in their argument list. This operation does not require administrator access.
 */
fun guessVmIdOfWsl(): UUID? {
  val process =
    ProcessBuilder("powershell", "-Command",
                   buildString {
                     append('"')
                     // `wslhost.exe` and `wslrelay.exe` are the processes that typically mention VmId in their command line
                     append("Get-WmiObject Win32_Process -Filter 'Name = ''wslhost.exe'' OR Name = ''wslrelay.exe''' ")
                     // we are interested only in the particular portion of the output
                     append("| Select-Object CommandLine ")
                     // Powershell trims the lines depending on the screen buffer size. List formatting prevents this behavior
                     append("| Format-List -Force ")
                     // Prevent Powershell from inserting random linebreaks to a commandline.
                     append("| Out-String -Width 2000000000")
                     append('"')
                   })
      .start()
  val vmIds = process.inputStream.bufferedReader().useLines { lines ->
    lines.mapNotNullTo(HashSet()) { line ->
      CONN_MGR_LOG.trace("Finding vmId in line: '''$line'''")
      val args = line.split(' ')
      val vmIdArg = args.indexOf("--vm-id") + 1
      if (vmIdArg > 0) {
        args.getOrNull(vmIdArg)
      }
      else {
        null
      }
    }
  }
  if (vmIds.size > 1) {
    // This should not be possible, but who knows.
    // We can have multiple _distro ids_ though, but there is a single VmId of WSL that everyone shares.
    CONN_MGR_LOG.warn("Unexpected multiple VmId from helper processes: ${vmIds}")
    return null
  }
  val singleVmId = vmIds.singleOrNull() ?: return null
  // Strip curly braces from Windows representation of GUID.
  val strippedVmId = singleVmId.substring(1, singleVmId.length - 1)
  return UUID.fromString(strippedVmId)
}
