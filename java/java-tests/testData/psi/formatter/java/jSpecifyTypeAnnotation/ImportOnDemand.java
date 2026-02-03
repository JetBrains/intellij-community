package org.example;

import org.jspecify.annotations.*;
import java.util.List;

public class Formatter {
    @NonNull
    <T, V> List<T> getNotNullList() {
        return List.of();
    }

    @Nullable
    <T, V> List<T> getNullableList() {
        return null;
    }

    @NonNull
    String getNotNullName() {
        return "";
    }

    @Nullable
    String getNullableName() {
        return null;
    }
}