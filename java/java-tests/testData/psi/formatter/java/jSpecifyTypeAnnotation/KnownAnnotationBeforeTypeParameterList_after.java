package org.example;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class Formatter {
    @NonNull <T, V> List<T> getNotNullList() {
        return List.of();
    }

    @Nullable <T, V> List<T> getNullableList() {
        return null;
    }
}
