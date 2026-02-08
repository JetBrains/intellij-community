// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed.util;

import org.jetbrains.annotations.ApiStatus;

import java.util.Base64;

/**
 * Utility class for creating data: URIs that can be passed to CefBrowser.loadURL.
 */
@SuppressWarnings("ALL")
@ApiStatus.Internal
public final class DataUri {
    public static String create(String mimeType, String contents) {
        return "data:" + mimeType + ";base64,"
                + Base64.getEncoder().encodeToString(contents.getBytes());
    }
}
