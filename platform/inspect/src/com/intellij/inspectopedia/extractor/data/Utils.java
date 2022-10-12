// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.inspectopedia.extractor.data;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class Utils {

    @Contract(pure = true)
    public static @NotNull String safeId(@NotNull String id) {
        return id.replaceAll("[^\\w\\d]", "-")
                .replaceAll("-{2,}", "-");
    }
}
