// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/***
 * Install `iperf3` both on Windows and WSL.
 * On Windows run `iperf3 -s`.
 * Run this action, copy command to Linux, and check bandwidth.
 * You might also add `-R` and `-P 3`. See iperf3 doc.
 */

@ApiStatus.Internal
package com.intellij.execution.wsl.proxy;

import org.jetbrains.annotations.ApiStatus;