package org.example;

public class Formatter {
    @org.jetbrains.annotations.NotNull
    <T, V> List<T> getNotNullList() {
        return List.of();
    }

    @org.jetbrains.annotations.Nullable
    <T, V> List<T> getNullableList() {
        return null;
    }

    @org.jetbrains.annotations.NotNull
    String getNotNullName() {
        return "";
    }

    @org.jetbrains.annotations.Nullable
    String getNullableName() {
        return null;
    }
}
