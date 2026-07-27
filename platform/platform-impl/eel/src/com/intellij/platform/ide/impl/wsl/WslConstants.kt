// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl

/**
 * Host names under which WSL exposes distributions as UNC shares, i.e. `\\wsl$\Ubuntu` and `\\wsl.localhost\Ubuntu`.
 *
 * `wsl$` is the legacy name and `wsl.localhost` is the current one, but both remain reachable, and paths coming from
 * users, tools and configuration files may use either. Therefore, any code that recognizes or compares WSL roots must
 * accept all of these names rather than a single one.
 *
 * Comparisons are case-insensitive on the Windows side, so these constants are kept lowercase.
 */
internal val WSL_PREFIXES: Array<String> = arrayOf("wsl$", "wsl.localhost")
