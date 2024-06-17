package org.example;

import org.jetbrains.annotations.*;
import java.util.List;

public class Formatter {
    @NotNull @org.jetbrains.annotations.Nullable
    <T, V> List<T> getNotNullList() {
        return List.of();
    }

    @NotNull @org.jetbrains.annotations.Nullable
    String getNotNullName() {
        return "";
    }
}