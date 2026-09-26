package org.example;

import org.jspecify.annotations.*;
import java.util.List;

public class Formatter {
    @NonNull @org.jspecify.annotations.Nullable
    <T, V> List<T> getNotNullList() {
        return List.of();
    }

    @NonNull @org.jspecify.annotations.Nullable
    String getNotNullName() {
        return "";
    }
}