package org.example;

import org.jetbrains.annotations.*;

import java.util.List;

public class Formatter {
    @NotNull <T, V> List<T> getNotNullList() {
        return List.of();
    }

    @Nullable <T, V> List<T> getNullableList() {
        return null;
    }

    @NotNull String getNotNullName() {
        return "";
    }

    @Nullable String getNullableName() {
        return null;
    }
}