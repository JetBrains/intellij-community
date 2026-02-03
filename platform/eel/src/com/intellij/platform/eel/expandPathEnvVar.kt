// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus

/**
 * Expands environment variables in a path string based on OS family.
 *
 * **Platform-Specific Behavior:**
 *
 * **Windows:** Expands %VARIABLE% syntax (e.g., %USERPROFILE%, %APPDATA%)
 *
 * **Rationale:** While no official Windows specification explicitly requires or prohibits
 * environment variable expansion in PATH, real-world usage shows PATH entries can contain unexpanded
 * variables. This function handles such cases to prevent parsing failures.
 *
 * See:
 * - [Environment Variables (MSDN)](https://learn.microsoft.com/en-us/windows/win32/procthread/environment-variables)
 * - [PathFindOnPathW (does not specify PATH format)](https://learn.microsoft.com/en-us/windows/win32/api/shlwapi/nf-shlwapi-pathfindonpathw)
 * - [SearchPathW (does not specify PATH format)](https://learn.microsoft.com/en-us/windows/win32/api/processenv/nf-processenv-searchpathw)
 * - [CreateProcessW (does not specify PATH format)](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-createprocessw)
 *
 * **POSIX (Unix/Linux/macOS):** NO expansion - returns path unchanged
 *
 * **Rationale:** POSIX explicitly specifies PATH as literal directory paths with colon separators.
 * No variable substitution is mentioned or allowed per the specification.
 *
 * See:
 * - [POSIX Environment Variables (IEEE Std 1003.1-2017)](https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap08.html) -
 *   "PATH shall represent the sequence of path prefixes... The prefixes shall be separated by a <colon>"
 * - [POSIX exec Functions (IEEE Std 1003.1-2017)](https://pubs.opengroup.org/onlinepubs/9699919799/functions/exec.html) -
 *   No mention of variable expansion in PATH
 *
 * Undefined variables on Windows are left unexpanded in the result.
 *
 * @param this@expandPathEnvVar the operating system family (Windows or Posix)
 * @param envVars the environment variables map
 * @return on Windows: path with %VARIABLES% expanded; on POSIX: path unchanged
 */
@ApiStatus.Experimental
fun EelOsFamily.expandPathEnvVar(envVars: Map<String, String>): String? {
  return when (this) {
    EelOsFamily.Windows -> expandWindowsPathEnvVar(envVars)
    EelOsFamily.Posix -> envVars["PATH"]  // POSIX spec forbids variable substitution in PATH.
  }
}

/**
 * A shortcut for [EelOsFamily.expandPathEnvVar].
 */
@Suppress("checkedExceptions")
@ApiStatus.Experimental
suspend fun EelExecApi.expandPathEnvVar(): String? =
  descriptor.osFamily.expandPathEnvVar(environmentVariables().eelIt().await())

/**
 * Expands Windows environment variables in a path string.
 * Supports %VARIABLE% syntax (case-insensitive).
 * Undefined variables are left unexpanded.
 *
 * Per MSDN: Variable names consist of alphanumeric characters and underscores, case-insensitive.
 * See: https://learn.microsoft.com/en-us/windows/win32/procthread/environment-variables
 */
private fun expandWindowsPathEnvVar(envVars: Map<String, String>): String? {
  val path = envVars["Path"] ?: return null

  @Suppress("StringReferentialEquality")
  require(envVars["PATH"] === path) {
    "Environment variables map on Windows must be case-insensitive, but got ${envVars.javaClass}"
  }

  return WINDOWS_PATH_ENV_VAR_REGEX.replace(path) { matchResult ->
    // If variable exists in environment, use its value; otherwise keep the original %VAR% syntax
    envVars[matchResult.groupValues[1]] ?: matchResult.value
  }
}

private val WINDOWS_PATH_ENV_VAR_REGEX = Regex("%([A-Z_][A-Z0-9_]*)%", RegexOption.IGNORE_CASE)
