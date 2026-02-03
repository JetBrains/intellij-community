package org.example;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Formatter {
    @NotNull @Nullable <T, V> List<T> getStrangeList() {
        return List.of();
    }

    @NotNull @Nullable String getNotNullName() {
        return "";
    }
}
