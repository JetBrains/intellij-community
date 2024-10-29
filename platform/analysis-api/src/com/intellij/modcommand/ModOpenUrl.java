// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import org.jetbrains.annotations.NotNull;

/**
 * An interactive command that allows to open an URL in the browser.
 * 
 * @param url URL to open
 */
public record ModOpenUrl(@NotNull String url) implements ModCommand {
}
