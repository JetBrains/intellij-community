package org.example;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class Formatter {
    @NonNull
    @Nullable
    <T, V> List<T> getStrangeList() {
        return List.of();
    }

    @NonNull
    @Nullable
    String getNotNullName() {
        return "";
    }
}
