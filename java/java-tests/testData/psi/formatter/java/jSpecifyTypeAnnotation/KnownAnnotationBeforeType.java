package org.example;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class Formatter {
    @NonNull String getNotNullName() {
        return "";
    }

    @Nullable String getNullableName() {
        return null;
    }
}